package com.liveseat.payment.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liveseat.common.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

@Component
public class AlipaySandboxClient {
    private final String appId;
    private final String gatewayUrl;
    private final String privateKeyText;
    private final String publicKeyText;
    private final String returnUrl;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    public AlipaySandboxClient(@Value("${alipay.app-id}") String appId,
                               @Value("${alipay.gateway-url}") String gatewayUrl,
                               @Value("${alipay.private-key}") String privateKeyText,
                               @Value("${alipay.alipay-public-key}") String publicKeyText,
                               @Value("${alipay.return-url:http://localhost:5173/orders?alipayReturn=1}") String returnUrl,
                               ObjectMapper objectMapper) {
        this.appId = appId;
        this.gatewayUrl = gatewayUrl;
        this.privateKeyText = privateKeyText;
        this.publicKeyText = publicKeyText;
        this.returnUrl = returnUrl;
        this.objectMapper = objectMapper;
    }

    public PagePayRequest createPagePay(String orderNo, BigDecimal amount) {
        try {
            Map<String, Object> biz = new LinkedHashMap<>();
            biz.put("out_trade_no", orderNo);
            biz.put("product_code", "FAST_INSTANT_TRADE_PAY");
            biz.put("total_amount", amount.setScale(2).toPlainString());
            biz.put("subject", "LiveSeat 演唱会订单 " + orderNo);
            biz.put("timeout_express", "10m");
            Map<String, String> fields = signedFields("alipay.trade.page.pay", objectMapper.writeValueAsString(biz), returnUrl);
            String pageGatewayUrl = gatewayUrl + (gatewayUrl.contains("?") ? "&" : "?") + "charset=UTF-8";
            return new PagePayRequest(pageGatewayUrl, fields);
        } catch (Exception exception) {
            throw new BusinessException(500, "支付宝支付参数生成失败");
        }
    }

    public QueryResult query(String orderNo) {
        try {
            Map<String, Object> biz = Map.of("out_trade_no", orderNo);
            Map<String, String> fields = signedFields("alipay.trade.query", objectMapper.writeValueAsString(biz), null);
            String body = formBody(fields);
            HttpRequest request = HttpRequest.newBuilder(URI.create(gatewayUrl))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException(502, "支付宝沙箱查询暂时不可用");
            }
            return parseQueryResponse(response.body());
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException(502, "支付宝沙箱查询失败: " + exception.getMessage());
        }
    }

    private QueryResult parseQueryResponse(String body) throws Exception {
        JsonNode envelope = objectMapper.readTree(body);
        JsonNode response = envelope.path("alipay_trade_query_response");
        String rawResponse = extractObject(body, "alipay_trade_query_response");
        String sign = envelope.path("sign").asText("");
        if (!sign.isBlank() && !verify(rawResponse, sign)) {
            throw new BusinessException(502, "支付宝响应验签失败");
        }
        String code = response.path("code").asText();
        String subCode = response.path("sub_code").asText();
        if (!"10000".equals(code)) {
            if ("ACQ.TRADE_NOT_EXIST".equals(subCode)) {
                return new QueryResult("TRADE_NOT_EXIST", null, orderValue(response, "out_trade_no"), null,
                        response.path("sub_msg").asText("交易尚未创建"));
            }
            throw new BusinessException(502, response.path("sub_msg").asText(response.path("msg").asText("支付宝查询失败")));
        }
        BigDecimal totalAmount = response.hasNonNull("total_amount")
                ? new BigDecimal(response.path("total_amount").asText()) : null;
        return new QueryResult(response.path("trade_status").asText("UNKNOWN"),
                response.path("trade_no").asText(null), orderValue(response, "out_trade_no"), totalAmount,
                response.path("msg").asText("Success"));
    }

    private Map<String, String> signedFields(String method, String bizContent, String returnUrlValue) throws Exception {
        Map<String, String> fields = new TreeMap<>();
        fields.put("app_id", appId);
        fields.put("biz_content", bizContent);
        fields.put("charset", "UTF-8");
        fields.put("format", "JSON");
        fields.put("method", method);
        fields.put("sign_type", "RSA2");
        fields.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        fields.put("version", "1.0");
        if (returnUrlValue != null && !returnUrlValue.isBlank()) fields.put("return_url", returnUrlValue);
        fields.put("sign", sign(canonical(fields)));
        return fields;
    }

    private String canonical(Map<String, String> fields) {
        return fields.entrySet().stream().filter(entry -> !"sign".equals(entry.getKey()) && entry.getValue() != null && !entry.getValue().isBlank())
                .map(entry -> entry.getKey() + "=" + entry.getValue()).reduce((left, right) -> left + "&" + right).orElse("");
    }

    private String sign(String content) throws Exception {
        PrivateKey privateKey = KeyFactory.getInstance("RSA").generatePrivate(
                new PKCS8EncodedKeySpec(Base64.getDecoder().decode(cleanKey(privateKeyText))));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(privateKey);
        signature.update(content.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private boolean verify(String content, String signText) throws Exception {
        PublicKey publicKey = KeyFactory.getInstance("RSA").generatePublic(
                new X509EncodedKeySpec(Base64.getDecoder().decode(cleanKey(publicKeyText))));
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(publicKey);
        signature.update(content.getBytes(StandardCharsets.UTF_8));
        return signature.verify(Base64.getDecoder().decode(signText));
    }

    private String cleanKey(String value) {
        return value.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
    }

    private String formBody(Map<String, String> fields) {
        return fields.entrySet().stream().map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .reduce((left, right) -> left + "&" + right).orElse("");
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private String extractObject(String json, String field) {
        int fieldIndex = json.indexOf('"' + field + '"');
        int start = fieldIndex < 0 ? -1 : json.indexOf('{', fieldIndex);
        if (start < 0) throw new BusinessException(502, "支付宝响应格式无效");
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;
        for (int index = start; index < json.length(); index++) {
            char current = json.charAt(index);
            if (escaped) { escaped = false; continue; }
            if (current == '\\' && quoted) { escaped = true; continue; }
            if (current == '"') { quoted = !quoted; continue; }
            if (!quoted && current == '{') depth++;
            if (!quoted && current == '}' && --depth == 0) return json.substring(start, index + 1);
        }
        throw new BusinessException(502, "支付宝响应格式无效");
    }

    private String orderValue(JsonNode response, String name) {
        return response.hasNonNull(name) ? response.path(name).asText() : null;
    }

    public record PagePayRequest(String gatewayUrl, Map<String, String> fields) {}
    public record QueryResult(String tradeStatus, String tradeNo, String outTradeNo, BigDecimal totalAmount, String message) {
        public boolean paid() { return "TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus); }
        public boolean closed() { return "TRADE_CLOSED".equals(tradeStatus); }
    }
}
