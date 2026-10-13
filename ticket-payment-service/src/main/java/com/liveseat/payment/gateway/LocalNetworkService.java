package com.liveseat.payment.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class LocalNetworkService {
    private final String configuredOrigin;
    private final int frontendPort;

    public LocalNetworkService(@Value("${wechat.mock.public-origin:}") String configuredOrigin,
                               @Value("${wechat.mock.frontend-port:5173}") int frontendPort) {
        this.configuredOrigin = configuredOrigin == null ? "" : configuredOrigin.trim();
        this.frontendPort = frontendPort;
    }

    public Map<String, Object> frontendOrigin() {
        if (!configuredOrigin.isBlank()) return result(configuredOrigin, "CONFIGURED");
        String address = defaultRouteAddress();
        if (address == null) address = firstUsableAddress();
        if (address == null) return result("http://localhost:" + frontendPort, "FALLBACK");
        return result("http://" + address + ":" + frontendPort, "AUTO_DETECTED");
    }

    private String defaultRouteAddress() {
        try (DatagramSocket socket = new DatagramSocket()) {
            // UDP connect does not send a packet. It only asks the OS which local interface
            // would be used for the current default route.
            socket.connect(new InetSocketAddress("8.8.8.8", 53));
            InetAddress address = socket.getLocalAddress();
            return usable(address) ? address.getHostAddress() : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String firstUsableAddress() {
        try {
            for (NetworkInterface network : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!network.isUp() || network.isLoopback() || network.isVirtual()) continue;
                for (InetAddress address : Collections.list(network.getInetAddresses())) {
                    if (usable(address)) return address.getHostAddress();
                }
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private boolean usable(InetAddress address) {
        return address instanceof Inet4Address && address.isSiteLocalAddress()
                && !address.isLoopbackAddress() && !address.isLinkLocalAddress();
    }

    private Map<String, Object> result(String origin, String source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("origin", origin.replaceAll("/$", ""));
        result.put("source", source);
        return result;
    }
}
