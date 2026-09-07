-- Demo-only accounts. Initial password for all demo accounts: Demo123.
INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_admin', 'zOeizxrgRZic/JFPuVpBUg==:xWXvxTlDz+TMHc7vtlTIA5co9c9CGtcym4aYtr2LK7M=', 'Demo Administrator', 'ADMIN', 1, 0);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_academic_admin', '2URhsAIut9zD4Wpa2LitDg==:hqrBro/Sc1nex0VjnlgVqlFs+1hSpS0g/RfTZAnot2g=', 'Demo Academic Administrator', 'ACADEMIC_ADMIN', 1, 0);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_librarian', 'CyUGA2zztjSKYZHTcuFFVw==:P3kwUfUVmXevVzORB4/2AO72BYFFLnpKXD4k+Vs/6XE=', 'Demo Librarian', 'LIBRARIAN', 1, 0);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_store_manager', 'MlmQfJs4JPqfyzrOS2vWSA==:60rRuqawBtN2BIANRJmd3X++VrG/WgO0npwd09JfU4Y=', 'Demo Store Manager', 'STORE_MANAGER', 1, 0);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_student', 'IZBIc+YD2QyDs5+HFIF4yQ==:jZiW3CFhJ854HF2PQsi2QVG0VRdz+SdW59ig/fMh1MY=', 'Demo Student', 'STUDENT', 1, 0);

INSERT INTO tblUser(user_id, password_hash, display_name, role_code, active, force_password_change)
VALUES ('demo_teacher', 'cSoOs3pVGxBnmJO0OZy1Rg==:qmNTtyQn+Lprr8EEzSRs/ZNxtQKgSEzVy3WOSl7VYdQ=', 'Demo Teacher', 'TEACHER', 1, 0);

-- 选课模块演示课程。
INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('JAVA101', 'Java 程序设计', 3, 'ACTIVE');

INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('DB101', '数据库原理', 3, 'ACTIVE');

INSERT INTO tblCourse(course_id, course_name, credits, status)
VALUES ('NET101', '计算机网络', 3, 'ACTIVE');

-- 学籍与学业审查演示数据：演示账号已预先绑定档案；正式历史成绩由教务维护或导入。
INSERT INTO tblClass(class_id, class_name, department_name, major_name, grade_year)
VALUES ('SE2023-01', '软件工程2023级1班', '计算机科学与工程学院', '软件工程', 2023);

INSERT INTO tblStudent(student_id, user_id, student_name, gender, department_name, major_name, class_id, enrollment_year, status, phone, email)
VALUES ('demo_student', 'demo_student', 'Demo Student', '未知', '计算机科学与工程学院', '软件工程', 'SE2023-01', 2023, '在读', '', '');

INSERT INTO tblTeacher(teacher_id, user_id, teacher_name, department_name, title, active)
VALUES ('demo_teacher', 'demo_teacher', 'Demo Teacher', '计算机科学与工程学院', '讲师', 1);

INSERT INTO tblCourseOffering(offering_id, course_id, teacher_id, term, schedule, location, required_capacity, elective_capacity, cross_major_capacity, status)
VALUES ('offering-java-2025a', 'JAVA101', 'demo_teacher', '2025-2026-1', '周一第1-2节', '教学楼A201', 35, 0, 5, 'OPEN');

-- 容量占用辅助表只记录当前已选人数；新建教学班的三个容量池均从 0 开始。
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count)
VALUES ('offering-java-2025a', 'REQUIRED', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count)
VALUES ('offering-java-2025a', 'ELECTIVE', 0);
INSERT INTO tblCourseOfferingCapacityUsage(offering_id, capacity_bucket, used_count)
VALUES ('offering-java-2025a', 'CROSS_MAJOR', 0);

INSERT INTO tblCourseMeeting(offering_id, day_of_week, start_period, end_period, location)
VALUES ('offering-java-2025a', 1, 1, 2, '教学楼A201');

INSERT INTO tblTrainingPlan(plan_id, major_name, enrollment_year, status)
VALUES ('plan-se-2026', '软件工程', 2026, 'PUBLISHED');
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-se-2026', 'JAVA101', 1, 'REQUIRED', 0);
INSERT INTO tblTrainingPlanCourse(plan_id, course_id, recommended_term, selection_type, cross_major_allowed)
VALUES ('plan-se-2026', 'DB101', 1, 'ELECTIVE', 0);

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-java-demo-1', 'demo_student', 'JAVA101', 'offering-java-2025a', '2025-2026-1', 1, '首修', 86, 1, 3, NOW());

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-db-demo-1', 'demo_student', 'DB101', NULL, '2025-2026-1', 1, '首修', 52, 0, 0, NOW());

INSERT INTO tblCourseResult(result_id, student_id, course_id, offering_id, semester, attempt_no, attempt_type, score, passed, earned_credits, recorded_at)
VALUES ('result-db-demo-2', 'demo_student', 'DB101', NULL, '2025-2026-2', 2, '重修', 75, 1, 3, NOW());

INSERT INTO tblAcademicReview(review_id, student_id, total_earned_credits, required_earned_credits, failed_course_count, retake_course_count, graduation_ready, reviewed_by, reviewed_at, remark)
VALUES ('review-demo-student-1', 'demo_student', 6, 6, 0, 1, 1, 'demo_admin', NOW(), '演示数据：包含首修未通过和重修通过记录。');

-- 商店模块演示商品。
INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version)
VALUES ('P001', '黑色签字笔', 200, 2.0, '0.5mm 中性笔，流畅书写', '文具', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version)
VALUES ('P002', '笔记本 A5', 150, 5.0, '80页横线本，封面随机', '文具', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version)
VALUES ('P003', '矿泉水 550ml', 300, 1.5, '天然矿泉水', '零食饮料', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version)
VALUES ('P004', '薯片 60g', 100, 6.0, '原味薯片，酥脆可口', '零食饮料', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version)
VALUES ('P005', '抽纸 3连包', 80, 8.5, '三层加厚面巾纸', '日用品', 1, 0);

-- 商店模块扩展演示商品（长期测试数据：P001..P105 共 105 种，覆盖 8 个类别）。
-- 注意：脚本以「;」切分逐条执行，商品名/说明内不得含「;」与单引号「'」。
INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P006', '晨光按动中性笔 0.5mm', 120, 3.5, '黑蓝红多色可选 按动出芯 书写顺滑', '文具', 1, 0),
('P007', '得力订书机 装订机', 60, 12.0, '省力型可旋转底座 标配一盒订书钉', '文具', 1, 0),
('P008', 'A4 文件袋 透明网格', 200, 4.0, '加厚防水 分类收纳试卷资料', '文具', 1, 0),
('P009', '彩色记号笔 荧光 6 支装', 90, 8.0, '速干不晕染 划重点好帮手', '文具', 1, 0),
('P010', '固体胶棒 36g', 150, 3.0, '易涂不拉丝 粘贴牢固', '文具', 1, 0),
('P011', '便利贴 76x76mm 混色', 180, 4.5, '多色索引 随手记录备忘', '文具', 1, 0),
('P012', '自动铅笔 0.5mm', 110, 5.0, '金属笔夹 握感舒适', '文具', 1, 0),
('P013', '2B 橡皮擦 白色', 200, 2.0, '清洁力强 不伤纸张', '文具', 1, 0),
('P014', '线圈笔记本 B5', 130, 6.5, '100 页 可 180 度平摊', '文具', 1, 0),
('P015', '剪刀 中号 170mm', 100, 7.0, '防锈合金 儿童安全圆头可选', '文具', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P016', '可乐 330ml 罐装', 240, 2.5, '冰镇更爽 经典碳酸饮料', '零食饮料', 1, 0),
('P017', '苏打饼干 原味', 120, 5.5, '咸香酥脆 独立小包', '零食饮料', 1, 0),
('P018', '牛奶 250ml 盒装', 260, 3.0, '全脂纯牛奶 早餐好搭档', '零食饮料', 1, 0),
('P019', '奶茶 即冲粉 3 连包', 150, 8.0, '港式风味 热水即冲', '零食饮料', 1, 0),
('P020', '巧克力 榛子夹心', 100, 9.5, '丝滑夹心 补充能量', '零食饮料', 1, 0),
('P021', '能量饮料 250ml', 200, 5.0, '提神醒脑 运动前后饮用', '零食饮料', 1, 0),
('P022', '坚果混合装 100g', 90, 15.0, '每日坚果 五种混合', '零食饮料', 1, 0),
('P023', '话梅蜜饯 罐装', 80, 10.0, '酸甜开胃 休闲零嘴', '零食饮料', 1, 0),
('P024', '果汁 300ml 橙味', 180, 4.5, '真实果汁 冷饮更佳', '零食饮料', 1, 0),
('P025', '辣条 大面筋', 160, 3.0, '香辣筋道 怀旧味道', '零食饮料', 1, 0),
('P026', '燕麦片 即食 400g', 70, 18.0, '无糖纯燕麦 冲泡即食', '零食饮料', 1, 0),
('P027', '咖啡 三合一 袋装', 140, 2.0, '速溶奶咖 熬夜提神', '零食饮料', 1, 0),
('P028', '果冻 综合口味 桶装', 110, 6.0, 'QQ 弹弹 多种水果味', '零食饮料', 1, 0),
('P029', '薯片 番茄味 60g', 130, 6.0, '轻薄香脆 追剧必备', '零食饮料', 1, 0),
('P030', '运动饮料 600ml', 190, 5.5, '补充电解质 快速补水', '零食饮料', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P031', '洗衣液 500g', 90, 12.0, '深层去渍 衣物清香', '日用品', 1, 0),
('P032', '垃圾袋 加厚 100 只', 130, 6.0, '抽绳式 大容量耐用', '日用品', 1, 0),
('P033', '挂钩 强力无痕 4 个装', 170, 5.0, '免打孔 承重牢固', '日用品', 1, 0),
('P034', '收纳箱 中号 带盖', 60, 25.0, '透明可视 衣物杂物分类', '日用品', 1, 0),
('P035', '衣架 浸塑 10 个装', 150, 8.0, '防滑耐用 晾晒不变形', '日用品', 1, 0),
('P036', '雨伞 全自动折叠', 80, 22.0, '一键开收 晴雨两用', '日用品', 1, 0),
('P037', '保温杯 500ml', 100, 39.0, '316 不锈钢 长效保温', '日用品', 1, 0),
('P038', '毛巾 纯棉 3 条装', 120, 15.0, '柔软吸水 亲肤不掉毛', '日用品', 1, 0),
('P039', '电池 5 号 4 粒装', 200, 5.0, '碱性电池 遥控器适用', '日用品', 1, 0),
('P040', '鞋刷 硬毛清洁刷', 90, 3.0, '洗鞋去污 手柄舒适', '日用品', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P041', 'Type-C 数据线 1m', 180, 9.0, '快充支持 尼龙编织耐磨', '数码配件', 1, 0),
('P042', '手机支架 桌面折叠', 140, 12.0, '多角度调节 铝合金底座', '数码配件', 1, 0),
('P043', '蓝牙耳机 入耳式', 100, 79.0, '降噪通话 续航持久', '数码配件', 1, 0),
('P044', '充电宝 10000mAh', 90, 89.0, '双向快充 轻薄便携', '数码配件', 1, 0),
('P045', '鼠标 无线静音', 110, 45.0, '2.4G 连接 办公游戏两用', '数码配件', 1, 0),
('P046', '键盘膜 14 寸', 130, 10.0, '硅胶防尘 键位贴合', '数码配件', 1, 0),
('P047', 'U 盘 32G USB3.0', 160, 25.0, '高速读写 金属外壳', '数码配件', 1, 0),
('P048', '笔记本散热垫', 80, 49.0, '静音风扇 多档调速', '数码配件', 1, 0),
('P049', 'HDMI 转接头', 120, 18.0, '音视频同步 即插即用', '数码配件', 1, 0),
('P050', '手机贴膜 钢化膜', 200, 8.0, '高透抗指纹 附贴膜工具', '数码配件', 1, 0),
('P051', '桌面收纳盒 数据线整理', 150, 13.0, '理线分格 清爽桌面', '数码配件', 1, 0),
('P052', '笔记本内胆包 14 寸', 110, 35.0, '加绒防震 轻薄护机', '数码配件', 1, 0),
('P053', '耳机收纳包', 140, 8.0, '防缠绕 EVA 硬壳', '数码配件', 1, 0),
('P054', '摄像头遮挡盖', 220, 3.0, '隐私保护 粘贴式', '数码配件', 1, 0),
('P055', '三合一充电线', 170, 12.0, '苹果安卓 Type-C 通用', '数码配件', 1, 0),
('P056', '桌面理线器 3 个装', 190, 6.0, '磁吸理线 整洁办公', '数码配件', 1, 0),
('P057', '机械键盘 87 键', 70, 129.0, '青轴打字清脆 键帽可换', '数码配件', 1, 0),
('P058', '无线充电板 15W', 100, 45.0, '随放随充 兼容多机型', '数码配件', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P059', '羽毛球拍 入门款', 90, 68.0, '全碳素 含球拍包', '体育用品', 1, 0),
('P060', '篮球 7 号', 80, 89.0, '室内外通用 耐磨防滑', '体育用品', 1, 0),
('P061', '瑜伽垫 加厚', 110, 45.0, '防滑回弹 附绑带', '体育用品', 1, 0),
('P062', '跳绳 竞速轴承', 160, 15.0, '长度可调 计数可选', '体育用品', 1, 0),
('P063', '乒乓球拍 双拍套装', 120, 35.0, '含 3 颗球 对练入门', '体育用品', 1, 0),
('P064', '哑铃 5kg 一对', 60, 99.0, '环保包胶 防滑握把', '体育用品', 1, 0),
('P065', '臂力器 30kg', 70, 39.0, '家用健身 渐进负荷', '体育用品', 1, 0),
('P066', '运动水壶 750ml', 130, 25.0, '食品级材质 一键开启', '体育用品', 1, 0),
('P067', '健腹轮 自动回弹', 90, 42.0, '加宽轮距 附跪垫', '体育用品', 1, 0),
('P068', '握力器 可调节', 140, 18.0, '五档调力 训练前臂', '体育用品', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P069', '洗发水 400ml', 110, 28.0, '去屑控油 清爽留香', '个人护理', 1, 0),
('P070', '沐浴露 500ml', 120, 22.0, '滋润保湿 泡沫绵密', '个人护理', 1, 0),
('P071', '牙膏 120g', 200, 8.0, '清新口气 含氟防蛀', '个人护理', 1, 0),
('P072', '电动牙刷头 替换装 2 支', 90, 29.0, '深层清洁 软毛护龈', '个人护理', 1, 0),
('P073', '护手霜 随身装', 150, 12.0, '滋润不油腻 秋冬必备', '个人护理', 1, 0),
('P074', '洗面奶 100g', 140, 26.0, '温和洁净 控油祛痘', '个人护理', 1, 0),
('P075', '湿巾 80 抽 便携装', 220, 6.0, '温和无酒精 清洁保湿', '个人护理', 1, 0),
('P076', '牙线棒 50 支装', 170, 9.0, '薄荷味 清洁齿缝', '个人护理', 1, 0),
('P077', '镜子 折叠双面', 130, 15.0, '高清放大镜 桌面立式', '个人护理', 1, 0),
('P078', '指甲剪 套装 8 件', 100, 18.0, '不锈钢便携 附皮套', '个人护理', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P079', '台灯 护眼可调光', 90, 69.0, '无频闪 三档色温', '宿舍生活', 1, 0),
('P080', '床上折叠桌', 80, 45.0, '宿舍神器 轻便可收纳', '宿舍生活', 1, 0),
('P081', '耳塞 睡眠降噪 5 对', 180, 8.0, '柔软回弹 隔音助眠', '宿舍生活', 1, 0),
('P082', '眼罩 遮光 真丝内衬', 160, 12.0, '轻柔软绵 午休必备', '宿舍生活', 1, 0),
('P083', '插座 一转二 USB', 150, 35.0, '带儿童保护门 兼容快充', '宿舍生活', 1, 0),
('P084', '晾衣绳 可伸缩', 130, 10.0, '宿舍阳台 免钉固定', '宿舍生活', 1, 0),
('P085', '桌面小风扇 充电式', 120, 49.0, '静音大风量 USB 供电', '宿舍生活', 1, 0),
('P086', '保温饭盒 双层', 90, 39.0, '304 内胆 保温保冷', '宿舍生活', 1, 0),
('P087', '折叠凳 便携小马扎', 140, 22.0, '加厚钢管 承重稳固', '宿舍生活', 1, 0),
('P088', '宿舍门锁 密码挂锁', 110, 20.0, '四位密码 免钥匙', '宿舍生活', 1, 0),
('P089', '防潮除湿盒', 170, 13.0, '吸湿防霉 衣柜适用', '宿舍生活', 1, 0),
('P090', '蚊帐 支架式', 100, 35.0, '单人床 免打孔安装', '宿舍生活', 1, 0),
('P091', '多孔笔筒 桌面收纳', 200, 12.0, '分层分格 收纳文具杂物', '宿舍生活', 1, 0),
('P092', '小型药箱 家庭常备', 130, 28.0, '分层收纳 防尘密封', '宿舍生活', 1, 0),
('P093', '灭蚊灯 物理吸入式', 120, 39.0, '静音无味 USB 供电', '宿舍生活', 1, 0);

INSERT INTO tblProduct(product_id, name, stock, price, description, category, active, version) VALUES
('P094', '校园明信片 一套 10 张', 180, 10.0, '校园风景手绘 可寄可收藏', '文创纪念品', 1, 0),
('P095', '校徽钥匙扣', 220, 8.0, '金属烤漆 小巧别致', '文创纪念品', 1, 0),
('P096', '帆布包 校名款', 150, 25.0, '加厚帆布 大容量', '文创纪念品', 1, 0),
('P097', '马克杯 校训定制', 160, 22.0, '陶瓷 350ml 保温防烫', '文创纪念品', 1, 0),
('P098', '书签 金属镂空', 200, 6.0, '流苏挂坠 精美耐用', '文创纪念品', 1, 0),
('P099', 'T 恤 毕业纪念款', 130, 45.0, '纯棉印花 尺码齐全', '文创纪念品', 1, 0),
('P100', '笔记本 烫金校徽 A5', 170, 18.0, '硬壳封面 内页厚实', '文创纪念品', 1, 0),
('P101', '钢笔 纪念礼盒', 90, 88.0, '金属笔身 附礼盒墨水', '文创纪念品', 1, 0),
('P102', '钥匙扣 母校建筑', 200, 9.0, '树脂立体 送人自用两宜', '文创纪念品', 1, 0),
('P103', '文件夹 校园四季', 180, 8.0, '春季限定图案 收纳文件', '文创纪念品', 1, 0),
('P104', '徽章 专业学院款', 220, 5.0, '珐琅工艺 别针式', '文创纪念品', 1, 0),
('P105', '保温马克杯 两用', 140, 35.0, '可冲泡 可外带 密封杯盖', '文创纪念品', 1, 0);

-- 商店模块演示订单。
INSERT INTO tblOrder(order_id, user_id, product_id, quantity, total_price, order_date, product_name, unit_price)
VALUES ('demo-order-001', 'demo_student', 'P001', 5, 10.0, NOW(), '黑色签字笔', 2.0);

INSERT INTO tblOrder(order_id, user_id, product_id, quantity, total_price, order_date, product_name, unit_price)
VALUES ('demo-order-002', 'demo_student', 'P003', 2, 3.0, NOW(), '矿泉水 550ml', 1.5);

INSERT INTO tblOrder(order_id, user_id, product_id, quantity, total_price, order_date, product_name, unit_price)
VALUES ('demo-order-003', 'demo_teacher', 'P002', 3, 15.0, NOW(), '笔记本 A5', 5.0);

-- 商店模块演示购物车条目。
INSERT INTO tblCartItem(cart_item_id, user_id, product_id, quantity, added_at)
VALUES ('demo-cart-001', 'demo_student', 'P001', 2, NOW());

INSERT INTO tblCartItem(cart_item_id, user_id, product_id, quantity, added_at)
VALUES ('demo-cart-002', 'demo_student', 'P003', 1, NOW());

-- 校园钱包演示余额（单位：分）。给能购买的演示账号留足余额，便于直接体验购买/购物车结算；
-- 仍保留一张 100 元档（demo_admin 20000 分=200 元）演示小金额场景。1 元 = 100 分。
INSERT INTO tblBankAccount(user_id, balance_cents)
VALUES ('demo_student', 1000000);

INSERT INTO tblBankAccount(user_id, balance_cents)
VALUES ('demo_teacher', 1000000);

INSERT INTO tblBankAccount(user_id, balance_cents)
VALUES ('demo_admin', 20000);

INSERT INTO tblBankAccount(user_id, balance_cents)
VALUES ('demo_store_manager', 50000);

-- 图书馆模块演示馆藏。
INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, total_copies, available_copies, location)
VALUES ('B001', 'Java核心技术（卷I）', 'Cay S. Horstmann', '9787115547392', '计算机', '机械工业出版社', 3, 3, 'A-01');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, total_copies, available_copies, location)
VALUES ('B002', '算法导论', 'Thomas H. Cormen', '9787111407010', '计算机', '机械工业出版社', 2, 2, 'A-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, total_copies, available_copies, location)
VALUES ('B003', '红楼梦', '曹雪芹', '9787020002207', '文学', '人民文学出版社', 2, 2, 'B-01');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, total_copies, available_copies, location)
VALUES ('B004', '三体', '刘慈欣', '9787536692930', '科幻', '重庆出版社', 4, 4, 'B-02');

INSERT INTO tblBook(book_id, title, author, isbn, category, publisher, total_copies, available_copies, location)
VALUES ('B005', '高等数学（第七版）', '同济大学数学系', '9787040396638', '教材', '高等教育出版社', 5, 5, 'C-01');
