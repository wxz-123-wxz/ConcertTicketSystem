CREATE DATABASE IF NOT EXISTS live_ticket DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE live_ticket;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(30) NOT NULL,
    phone VARCHAR(20) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(30) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_phone (phone)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS venue (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    city VARCHAR(40) NOT NULL,
    address VARCHAR(200) NOT NULL,
    capacity INT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS concert (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(120) NOT NULL,
    artist VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    cover_url VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    sale_start_time DATETIME NOT NULL,
    sale_end_time DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_concert_status_sale (status, sale_start_time)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS show_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    concert_id BIGINT NOT NULL,
    venue_id BIGINT NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_session_concert_time (concert_id, start_time),
    CONSTRAINT fk_session_concert FOREIGN KEY (concert_id) REFERENCES concert(id),
    CONSTRAINT fk_session_venue FOREIGN KEY (venue_id) REFERENCES venue(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ticket_category (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    name VARCHAR(40) NOT NULL,
    area_name VARCHAR(40) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    color VARCHAR(20) NOT NULL,
    UNIQUE KEY uk_category_session_area (session_id, area_name),
    CONSTRAINT fk_category_session FOREIGN KEY (session_id) REFERENCES show_session(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS session_seat (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL,
    seat_code VARCHAR(20) NOT NULL,
    area_name VARCHAR(40) NOT NULL,
    row_no INT NOT NULL,
    col_no INT NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE',
    lock_token VARCHAR(64) NULL,
    lock_expire_time DATETIME NULL,
    sold_order_no VARCHAR(40) NULL,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_session_seat_code (session_id, seat_code),
    KEY idx_seat_session_status (session_id, status),
    CONSTRAINT fk_seat_session FOREIGN KEY (session_id) REFERENCES show_session(id),
    CONSTRAINT fk_seat_category FOREIGN KEY (category_id) REFERENCES ticket_category(id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS ticket_order (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    hold_token VARCHAR(64) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING_PAYMENT',
    expire_time DATETIME NOT NULL,
    paid_at DATETIME NULL,
    cancelled_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_order_user_created (user_id, created_at),
    KEY idx_order_status_expire (status, expire_time)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_item (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    seat_id BIGINT NOT NULL,
    seat_code VARCHAR(20) NOT NULL,
    area_name VARCHAR(40) NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    UNIQUE KEY uk_order_seat (order_no, seat_id),
    KEY idx_item_order (order_no)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS order_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_no VARCHAR(40) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NOT NULL,
    note VARCHAR(200) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_log_order (order_no, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS payment_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    payment_no VARCHAR(40) NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    channel VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    provider_trade_no VARCHAR(64) NULL,
    provider_status VARCHAR(32) NULL,
    last_checked_at DATETIME NULL,
    paid_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_payment_no (payment_no),
    UNIQUE KEY uk_payment_order (order_no)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS show_favorite (
    user_id BIGINT NOT NULL,
    concert_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, concert_id),
    KEY idx_favorite_user_created (user_id, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS show_subscription (
    user_id BIGINT NOT NULL,
    concert_id BIGINT NOT NULL,
    sale_reminder TINYINT(1) NOT NULL DEFAULT 0,
    session_reminder TINYINT(1) NOT NULL DEFAULT 0,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, concert_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS user_notification (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    concert_id BIGINT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(120) NOT NULL,
    content VARCHAR(300) NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_notification_user_read (user_id, is_read, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS refund_request (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    refund_no VARCHAR(40) NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    reason VARCHAR(300) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    admin_note VARCHAR(300) NULL,
    reviewed_by BIGINT NULL,
    reviewed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_refund_no (refund_no),
    UNIQUE KEY uk_refund_order (order_no),
    KEY idx_refund_status_created (status, created_at)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS electronic_ticket (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ticket_code VARCHAR(48) NOT NULL,
    order_no VARCHAR(40) NOT NULL,
    user_id BIGINT NOT NULL,
    seat_id BIGINT NOT NULL,
    seat_code VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'VALID',
    checked_in_at DATETIME NULL,
    checked_in_by BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ticket_code (ticket_code),
    UNIQUE KEY uk_ticket_order_seat (order_no, seat_id),
    KEY idx_ticket_user_status (user_id, status)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    operator_id BIGINT NOT NULL,
    operator_name VARCHAR(60) NOT NULL,
    action VARCHAR(40) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(60) NOT NULL,
    detail VARCHAR(500) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    KEY idx_audit_created (created_at),
    KEY idx_audit_operator (operator_id, created_at)
) ENGINE=InnoDB;

INSERT IGNORE INTO sys_user(id, username, phone, password_hash, nickname, role, status)
VALUES (1, 'admin', '13800000000', '$2b$12$w1EjCYK0m3xXDzqkuuoBLu15bYGPHW1WPvraopCvC3yyFKtDfX./.', '系统管理员', 'ADMIN', 'ACTIVE');

INSERT IGNORE INTO venue(id, name, city, address, capacity) VALUES
  (1, '梅赛德斯-奔驰文化中心', '上海', '浦东新区世博大道1200号', 18000),
  (2, '华熙LIVE·五棵松', '北京', '海淀区复兴路69号', 15000),
  (3, '宝能广州国际体育演艺中心', '广州', '黄埔区大塱村开创大道2666号', 18000),
  (4, '凤凰山体育公园综合体育馆', '成都', '金牛区北星大道一段', 18000),
  (5, '深圳湾体育中心体育馆', '深圳', '南山区滨海大道3001号', 13000),
  (6, '杭州奥体中心体育馆', '杭州', '萧山区博奥路2657号', 18000),
  (7, '武汉体育中心体育馆', '武汉', '蔡甸区车城北路58号', 13000);

INSERT IGNORE INTO concert(id, title, artist, description, cover_url, status, sale_start_time, sale_end_time) VALUES
  (1, '向光而行 2026 巡回演唱会', '林屿乐队', '在环形舞台与全景声场里，听见一支年轻乐队从校园走向万人场馆的十年。', '/assets/concert-main.webp', 'ON_SALE', '2026-07-01 10:00:00', '2026-12-01 20:00:00'),
  (2, '午夜电台 Live', '周末频率', '一场以城市夜行、合成器与流行摇滚为线索的现场演出。', '/assets/concert-night.webp', 'ON_SALE', '2026-07-08 10:00:00', '2026-11-01 20:00:00'),
  (3, '星海回响 2026 交响流行巡演', '陆遥与星河室内乐团', '弦乐、电子音色与流行人声在星幕舞台上交汇，呈现一场兼具叙事感与现场张力的交响流行演出。', '/assets/concert-starlight.webp', 'ON_SALE', '2026-07-18 10:00:00', '2026-10-18 20:00:00'),
  (4, '夏日逆风 音乐节特别场', '南岸少年', '以明亮吉他、青春合唱和环形延伸台串联夏日记忆，适合一起挥手合唱的热烈现场。', '/assets/concert-summerwind.webp', 'ON_SALE', '2026-07-20 10:00:00', '2026-09-05 20:00:00'),
  (5, '霓虹航线 Arena Tour', 'NOVA-7', '未来感合成器、机械节拍与城市天际线视觉共同构成一段穿越夜色的电子流行航程。', '/assets/concert-neon-route.webp', 'ON_SALE', '2026-07-22 10:00:00', '2026-11-15 20:00:00'),
  (6, '山海之间 民谣现场', '陈川与远山', '木吉他、手风琴与温暖人声讲述远行、故乡和重逢，在万人场馆里保留近距离围坐般的真诚。', '/assets/concert-mountain-sea.webp', 'ON_SALE', '2026-07-25 10:00:00', '2026-12-06 20:00:00'),
  (7, '热望觉醒 2026 巡回演唱会', '赤焰计划', '硬朗鼓点、双吉他与大合唱点燃全场，以克制的红色舞台语言呈现年轻、直接而有力量的摇滚现场。', '/assets/concert-awakening.webp', 'ON_SALE', '2026-07-28 10:00:00', '2026-12-20 20:00:00');

INSERT IGNORE INTO show_session(id, concert_id, venue_id, start_time, end_time, status) VALUES
  (1, 1, 1, '2026-09-19 19:30:00', '2026-09-19 22:00:00', 'ON_SALE'),
  (2, 1, 1, '2026-09-20 19:30:00', '2026-09-20 22:00:00', 'ON_SALE'),
  (3, 2, 2, '2026-10-03 20:00:00', '2026-10-03 22:30:00', 'ON_SALE'),
  (4, 3, 3, '2026-10-17 19:30:00', '2026-10-17 22:00:00', 'ON_SALE'),
  (5, 4, 4, '2026-09-05 19:00:00', '2026-09-05 21:30:00', 'ON_SALE'),
  (6, 5, 5, '2026-11-14 20:00:00', '2026-11-14 22:30:00', 'ON_SALE'),
  (7, 6, 6, '2026-12-05 19:30:00', '2026-12-05 22:00:00', 'ON_SALE'),
  (8, 7, 7, '2026-12-19 19:30:00', '2026-12-19 22:00:00', 'ON_SALE');

INSERT IGNORE INTO ticket_category(id, session_id, name, area_name, price, color) VALUES
  (1, 1, '内场票', 'A区', 1280.00, '#E4493D'), (2, 1, '看台票', 'B区', 680.00, '#17867A'),
  (3, 2, '内场票', 'A区', 1280.00, '#E4493D'), (4, 2, '看台票', 'B区', 680.00, '#17867A'),
  (5, 3, '内场票', 'A区', 980.00, '#E4493D'), (6, 3, '看台票', 'B区', 580.00, '#17867A'),
  (7, 4, '星幕内场', 'A区', 1080.00, '#E4493D'), (8, 4, '环形看台', 'B区', 480.00, '#17867A'),
  (9, 5, '浪潮内场', 'A区', 880.00, '#E4493D'), (10, 5, '青春看台', 'B区', 380.00, '#17867A'),
  (11, 6, '航线内场', 'A区', 1180.00, '#E4493D'), (12, 6, '霓虹看台', 'B区', 580.00, '#17867A'),
  (13, 7, '山海内场', 'A区', 780.00, '#E4493D'), (14, 7, '远山看台', 'B区', 280.00, '#17867A'),
  (15, 8, '热望内场', 'A区', 980.00, '#E4493D'), (16, 8, '觉醒看台', 'B区', 480.00, '#17867A');

DELIMITER //
CREATE PROCEDURE seed_seats()
BEGIN
    DECLARE s INT DEFAULT 1;
    DECLARE r INT;
    DECLARE c INT;
    DECLARE category_id INT;
    DECLARE seat_price DECIMAL(10,2);
    WHILE s <= 8 DO
        SET r = 1;
        WHILE r <= 8 DO
            SET c = 1;
            WHILE c <= 10 DO
                SET category_id = CASE WHEN r <= 3 THEN (s - 1) * 2 + 1 ELSE (s - 1) * 2 + 2 END;
                SELECT price INTO seat_price FROM ticket_category WHERE id = category_id;
                INSERT IGNORE INTO session_seat(session_id, category_id, seat_code, area_name, row_no, col_no, price)
                VALUES (s,
                        category_id,
                        CONCAT(CASE WHEN r <= 3 THEN 'A' ELSE 'B' END, '-', LPAD(r, 2, '0'), '-', LPAD(c, 2, '0')),
                        CASE WHEN r <= 3 THEN 'A区' ELSE 'B区' END,
                        r, c, seat_price);
                SET c = c + 1;
            END WHILE;
            SET r = r + 1;
        END WHILE;
        SET s = s + 1;
    END WHILE;
END//
DELIMITER ;
CALL seed_seats();
DROP PROCEDURE seed_seats;

INSERT IGNORE INTO electronic_ticket(ticket_code, order_no, user_id, seat_id, seat_code, status)
SELECT CONCAT('TKT', UPPER(HEX(RANDOM_BYTES(16)))), o.order_no, o.user_id, i.seat_id, i.seat_code, 'VALID'
FROM ticket_order o JOIN order_item i ON i.order_no = o.order_no
WHERE o.status = 'PAID';
