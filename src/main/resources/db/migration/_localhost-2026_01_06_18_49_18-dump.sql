-- MySQL dump 10.13  Distrib 8.4.5, for Win64 (x86_64)
--
-- Host: 127.0.0.1    Database: vd
-- ------------------------------------------------------
-- Server version	8.4.5

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `activities`
--

DROP TABLE IF EXISTS `activities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activities` (
  `id` varchar(64) NOT NULL,
  `functionary` varchar(100) NOT NULL,
  `name` varchar(200) NOT NULL,
  `type` varchar(50) NOT NULL,
  `description` text,
  `enrollment_start_time` datetime DEFAULT NULL,
  `enrollment_end_time` datetime DEFAULT NULL,
  `start_time` datetime DEFAULT NULL,
  `expected_end_time` datetime DEFAULT NULL,
  `cover_path` varchar(255) DEFAULT NULL,
  `status` varchar(50) NOT NULL,
  `is_full` tinyint(1) DEFAULT '0',
  `max_participants` int DEFAULT NULL,
  `duration` double NOT NULL COMMENT '志愿时长(小时)',
  `end_time` datetime DEFAULT NULL,
  `imported` tinyint(1) DEFAULT NULL,
  `rejected_reason` text,
  `reviewed_at` datetime DEFAULT NULL COMMENT '审核时间',
  `reviewed_by` varchar(20) DEFAULT NULL COMMENT '审核人学号',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `activities`
--

LOCK TABLES `activities` WRITE;
/*!40000 ALTER TABLE `activities` DISABLE KEYS */;
INSERT INTO `activities` VALUES ('42405638-feba-4ed5-85ef-8a0ae5a82758','12323020420','测试后台导入','COMMUNITY_SERVICE','测试后台导入','2025-12-16 21:17:32','2025-12-16 21:17:32','2025-12-16 21:17:32','2025-12-16 21:17:32','/covers/8a4f9ea1-a271-450c-a20a-532f5da40ece.png','ActivityEnded',1,4,20,'2025-12-16 21:17:32',1,NULL,NULL,NULL),('4c418a59-812c-4cfb-9baa-dc3856d3c606','12323020421','测试负责人后台导入','COMMUNITY_SERVICE','测试负责人后台导入excel','2025-12-16 21:20:34','2025-12-16 21:20:34','2025-12-16 21:20:34','2025-12-16 21:20:34','/covers/4b10beb9-2dfa-4a48-90e5-dc5919732f9a.png','ActivityEnded',1,3,4,'2025-12-16 21:20:34',1,NULL,NULL,NULL),('860ea40d-8442-4573-a80d-3af73688757a','12323020421','鸿蒙开发','COMMUNITY_SERVICE','开发鸿蒙应用','2025-11-26 00:00:00','2025-11-26 17:30:00','2025-11-29 00:00:00','2025-11-30 00:00:00','/covers/f099362a-c6b9-4f22-989a-38a7d17940df.png','ActivityEnded',0,10,0.5,NULL,NULL,NULL,NULL,NULL),('99ee1eba-bdc0-4bf1-a731-7de2bf7bfe76','12323020421','12345','CULTURE_SERVICE','测试','2025-11-27 08:00:00','2025-11-29 17:17:08','2025-11-30 00:00:00','2025-12-03 00:00:00','/covers/73f4b2ee-4644-4204-b0f6-dca3d8bd5a5b.png','ActivityEnded',0,13,2,NULL,NULL,NULL,NULL,NULL),('cf24f36c-9f52-4312-aefc-c46467e0a0e6','12323020420','测试','COMMUNITY_SERVICE','测试','2025-12-16 10:22:20','2025-12-16 10:22:20','2025-12-16 10:22:20','2025-12-16 10:22:20',NULL,'ActivityEnded',1,1,20,'2025-12-16 10:22:20',1,NULL,NULL,NULL),('dacf373d-1167-425a-8466-8ccce14b3bbf','12323020421','测试','COMMUNITY_SERVICE','测试','2025-12-08 00:00:00','2025-12-09 00:00:00','2025-12-16 00:00:00','2025-12-19 00:00:00',NULL,'UnderReview',0,10,0.5,NULL,NULL,NULL,NULL,NULL);
/*!40000 ALTER TABLE `activities` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `activity_attachments`
--

DROP TABLE IF EXISTS `activity_attachments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_attachments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `activity_id` varchar(64) NOT NULL,
  `path` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `activity_attachments`
--

LOCK TABLES `activity_attachments` WRITE;
/*!40000 ALTER TABLE `activity_attachments` DISABLE KEYS */;
/*!40000 ALTER TABLE `activity_attachments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `activity_participants`
--

DROP TABLE IF EXISTS `activity_participants`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `activity_participants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `activity_id` varchar(64) NOT NULL,
  `student_no` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_activity_student` (`activity_id`,`student_no`)
) ENGINE=InnoDB AUTO_INCREMENT=112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `activity_participants`
--

LOCK TABLES `activity_participants` WRITE;
/*!40000 ALTER TABLE `activity_participants` DISABLE KEYS */;
INSERT INTO `activity_participants` VALUES (21,'42405638-feba-4ed5-85ef-8a0ae5a82758','12323020334'),(22,'42405638-feba-4ed5-85ef-8a0ae5a82758','12323020406'),(23,'42405638-feba-4ed5-85ef-8a0ae5a82758','12323020420'),(24,'42405638-feba-4ed5-85ef-8a0ae5a82758','12423020320'),(25,'4c418a59-812c-4cfb-9baa-dc3856d3c606','12323020406'),(26,'4c418a59-812c-4cfb-9baa-dc3856d3c606','12323020420');
/*!40000 ALTER TABLE `activity_participants` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pending_activities`
--

DROP TABLE IF EXISTS `pending_activities`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pending_activities` (
  `id` varchar(64) NOT NULL,
  `functionary` varchar(100) NOT NULL,
  `name` varchar(200) NOT NULL,
  `type` varchar(50) NOT NULL,
  `description` text,
  `duration` double NOT NULL,
  `end_time` datetime NOT NULL,
  `cover_path` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `submitted_by` varchar(100) NOT NULL,
  `reviewed_at` datetime DEFAULT NULL COMMENT '审核时间',
  `reviewed_by` varchar(20) DEFAULT NULL COMMENT '审核人学号',
  `rejected_reason` varchar(500) DEFAULT NULL COMMENT '拒绝理由',
  `status` varchar(30) DEFAULT 'PENDING' COMMENT '审核状态: PENDING, APPROVED, REJECTED',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pending_activities`
--

LOCK TABLES `pending_activities` WRITE;
/*!40000 ALTER TABLE `pending_activities` DISABLE KEYS */;
/*!40000 ALTER TABLE `pending_activities` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pending_attachments`
--

DROP TABLE IF EXISTS `pending_attachments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pending_attachments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pending_activity_id` varchar(64) NOT NULL,
  `path` varchar(255) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pending_attachments`
--

LOCK TABLES `pending_attachments` WRITE;
/*!40000 ALTER TABLE `pending_attachments` DISABLE KEYS */;
/*!40000 ALTER TABLE `pending_attachments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pending_batch_import_records`
--

DROP TABLE IF EXISTS `pending_batch_import_records`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pending_batch_import_records` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `batch_id` varchar(64) NOT NULL COMMENT '批次ID',
  `username` varchar(80) DEFAULT NULL COMMENT '姓名',
  `gender` varchar(10) DEFAULT NULL COMMENT '性别',
  `college` varchar(100) DEFAULT NULL COMMENT '学院',
  `grade` varchar(20) DEFAULT NULL COMMENT '年级',
  `student_no` varchar(20) NOT NULL COMMENT '学号',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系方式',
  `duration` double DEFAULT NULL COMMENT '服务时长（小时）',
  `activity_name` varchar(200) NOT NULL COMMENT '活动名称（规范化后）',
  `original_activity_name` varchar(200) DEFAULT NULL COMMENT '原始活动名称',
  `user_exists` tinyint(1) NOT NULL DEFAULT '0' COMMENT '用户是否已存在',
  PRIMARY KEY (`id`),
  KEY `idx_batch_id` (`batch_id`),
  KEY `idx_student_no` (`student_no`),
  KEY `idx_activity_name` (`activity_name`)
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pending_batch_import_records`
--

LOCK TABLES `pending_batch_import_records` WRITE;
/*!40000 ALTER TABLE `pending_batch_import_records` DISABLE KEYS */;
INSERT INTO `pending_batch_import_records` VALUES (1,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','姓名','性别','学院','年级','学号','联系方式',NULL,'活动名称','活动名称',0),(2,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','杨凌','男','材料学院','研24级','52240908217','18708445060',24,'材料学院2025年教师职称评审材料整理','材料学院2025年教师职称评审材料整理（24小时）',0),(3,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','李秋蓉','女','材料学院','研25级','52250908320','15696719507',24,'材料学院2025年教师职称评审材料整理','材料学院2025年教师职称评审材料整理（24小时）',0),(4,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','王国微','男','材料学院','研24级','52240908235','18084143611',6,'材控工程认证会场布置','材控工程认证会场布置（4小时）',0),(5,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','王国微','男','材料学院','研24级','52240908235','18084143611',6,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(6,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','曹智','男','材料学院','研25级','52250908118','18295280692',4,'材料工程认证会场布置','材料工程认证会场布置（4小时）',0),(7,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','夏胡林','男','材料学院','研24级','52240908210','13368019324',4,'材控工程认证会场布置','材控工程认证会场布置（4小时）',0),(8,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','陈晓龙','男','材料学院','研24级','52240908327','17502330137',4,'材控工程认证会场布置','材控工程认证会场布置（4小时）',0),(9,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','马玲','女','材料学院','研24级','52240908333','15223605750',4,'材控工程认证会场布置','材控工程认证会场布置（4小时）',0),(10,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','孙洁','女','材料学院','研24级','52240908238','19922231735',4,'材控工程认证会场布置','材控工程认证会场布置（2小时）',0),(11,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','孙洁','女','材料学院','研24级','52240908238','19922231735',4,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(12,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','杜海行','男','材料学院','研24级','52240908311','17353134314',4,'材控工程认证会场布置','材控工程认证会场布置（2小时）',0),(13,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','杜海行','男','材料学院','研24级','52240908311','17353134314',4,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(14,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','杨宇','女','材料学院','研24级','51240907118','17772392865',4,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(15,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','陈显环','女','材料学院','研24级','52240908137','18723668382',2,'材控工程认证会场布置','材控工程认证会场布置（2小时）',0),(16,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','魏森杰','男','材料学院','研24级','52240908122','17628221428',2,'材控工程认证会场布置','材控工程认证会场布置（2小时）',0),(17,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','汪堞炜','男','材料学院','研25级','52250908328','17313789985',2,'材控工程认证会场布置','材控工程认证会场布置（2小时）',0),(18,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','王智杰','男','材料学院','22级','12209010129','19823784705',2,'材控工程认证会场布置','材控工程认证会场布置（2小时）',0),(19,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','刘江萍','女','材料学院','研24级','52240908140','13372736516',2,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(20,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','冯雨婷','女','材料学院','研24级','52240908105','17774932991',2,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(21,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','李红莉','女','材料学院','研24级','52240908128','13881236159',2,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(22,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','杨梓仪','女','材料学院','研24级','52240908113','18323012593',2,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(23,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','王世辉','男','材料学院','研23级','52230908330','13368473046',2,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(24,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','杨义盟','男','材料学院','研24级','51240907102','13648397896',2,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(25,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','罗杉','女','材料学院','研24级','52240908114','18875446380',2,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(26,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','李方樑','男','材料学院','研23级','52230908332','15909308393',2,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0),(27,'741de7ea-923c-4e52-9f9c-efadcdd04d9e','宋春仪','男','材料学院','研24级','52240908326','15631861973',2,'材梓农场志愿服务活动','材梓农场志愿服务活动（2小时）',0);
/*!40000 ALTER TABLE `pending_batch_import_records` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pending_batch_imports`
--

DROP TABLE IF EXISTS `pending_batch_imports`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pending_batch_imports` (
  `id` varchar(64) NOT NULL,
  `submitted_by` varchar(100) NOT NULL COMMENT '提交人学号',
  `original_filename` varchar(255) DEFAULT NULL COMMENT '原始文件名',
  `total_records` int NOT NULL DEFAULT '0' COMMENT '总记录数',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/APPROVED/REJECTED',
  `reviewed_at` datetime DEFAULT NULL COMMENT '审核时间',
  `reviewed_by` varchar(100) DEFAULT NULL COMMENT '审核人学号',
  `rejected_reason` varchar(500) DEFAULT NULL COMMENT '拒绝理由',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pending_batch_imports`
--

LOCK TABLES `pending_batch_imports` WRITE;
/*!40000 ALTER TABLE `pending_batch_imports` DISABLE KEYS */;
INSERT INTO `pending_batch_imports` VALUES ('741de7ea-923c-4e52-9f9c-efadcdd04d9e','12323020421','材料学院12月时长文件.xlsx',24,'2026-01-04 12:40:05','PENDING',NULL,NULL,NULL);
/*!40000 ALTER TABLE `pending_batch_imports` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `pending_participants`
--

DROP TABLE IF EXISTS `pending_participants`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pending_participants` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `pending_activity_id` varchar(64) NOT NULL,
  `student_no` varchar(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_pending_activity_student` (`pending_activity_id`,`student_no`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `pending_participants`
--

LOCK TABLES `pending_participants` WRITE;
/*!40000 ALTER TABLE `pending_participants` DISABLE KEYS */;
/*!40000 ALTER TABLE `pending_participants` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `personal_hour_request_attachments`
--

DROP TABLE IF EXISTS `personal_hour_request_attachments`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `personal_hour_request_attachments` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `request_id` varchar(64) NOT NULL,
  `path` varchar(255) NOT NULL COMMENT '附件路径',
  PRIMARY KEY (`id`),
  KEY `idx_personal_hour_request_attachments_request_id` (`request_id`),
  CONSTRAINT `personal_hour_request_attachments_ibfk_1` FOREIGN KEY (`request_id`) REFERENCES `personal_hour_requests` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `personal_hour_request_attachments`
--

LOCK TABLES `personal_hour_request_attachments` WRITE;
/*!40000 ALTER TABLE `personal_hour_request_attachments` DISABLE KEYS */;
INSERT INTO `personal_hour_request_attachments` VALUES (1,'ef3c94b4-b63b-4742-8477-f35feac4679e','/attachments/831a6454-0878-4b39-87e6-36197d7a11d2_README.md');
/*!40000 ALTER TABLE `personal_hour_request_attachments` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `personal_hour_requests`
--

DROP TABLE IF EXISTS `personal_hour_requests`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `personal_hour_requests` (
  `id` varchar(64) NOT NULL,
  `applicant_student_no` varchar(20) NOT NULL COMMENT '申请人学号',
  `name` varchar(200) NOT NULL COMMENT '活动名称',
  `functionary` varchar(100) NOT NULL COMMENT '证明人/负责人姓名',
  `type` varchar(50) NOT NULL COMMENT '活动类型',
  `description` text COMMENT '活动简述',
  `start_time` datetime NOT NULL COMMENT '活动开始时间',
  `end_time` datetime NOT NULL COMMENT '活动结束时间',
  `duration` double NOT NULL COMMENT '申请时长',
  `status` varchar(30) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态: PENDING, APPROVED, REJECTED',
  `rejected_reason` varchar(500) DEFAULT NULL COMMENT '拒绝理由',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
  `reviewed_at` datetime DEFAULT NULL COMMENT '审核时间',
  `reviewed_by` varchar(20) DEFAULT NULL COMMENT '审核人学号',
  PRIMARY KEY (`id`),
  KEY `idx_personal_hour_requests_applicant` (`applicant_student_no`),
  KEY `idx_personal_hour_requests_status` (`status`),
  KEY `idx_personal_hour_requests_created_at` (`created_at`),
  CONSTRAINT `personal_hour_requests_ibfk_1` FOREIGN KEY (`applicant_student_no`) REFERENCES `users` (`student_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `personal_hour_requests`
--

LOCK TABLES `personal_hour_requests` WRITE;
/*!40000 ALTER TABLE `personal_hour_requests` DISABLE KEYS */;
INSERT INTO `personal_hour_requests` VALUES ('28b1c63c-81d4-4b09-8328-1c81a0a2ac5e','12323020420','测试附件','黄智哲','MEDICAL_ASSISTANCE','测试','2025-12-16 00:00:00','2025-12-18 00:00:00',3,'UnderReview',NULL,'2025-12-17 09:46:28',NULL,NULL),('642a6d68-6b6b-4c30-b801-a2d9a2b871e3','12323020406','测试罗正','高永旗','EMERGENCY_RESCUE','测试','2025-12-16 00:00:00','2025-12-18 00:00:00',0.5,'UnderReview',NULL,'2025-12-17 08:44:38',NULL,NULL),('72e6b2db-536e-4d0b-8039-a143771c17a7','12323020406','测试罗正2','高永旗','POVERTY_ASSISTANCE','测试','2025-12-16 00:00:00','2025-12-18 00:00:00',0.5,'UnderReview',NULL,'2025-12-17 08:56:08',NULL,NULL),('af0f9036-ff65-45a7-a030-7dfcb89c5d79','12323020420','测试3','罗正','POVERTY_ASSISTANCE','测试','2025-12-16 00:00:00','2025-12-18 00:00:00',0.5,'ActivityEnded',NULL,'2025-12-17 10:03:11','2025-12-17 12:30:02','12323020420'),('d9f6aeb6-6a0d-4205-b0a7-d2c74189ff4e','12323020420','测试管理员申请','黄智哲','CULTURE_SERVICE','测试','2025-12-03 00:00:00','2025-12-11 00:00:00',2,'FailReview','测试拒绝','2025-12-17 08:40:57','2025-12-17 09:44:49','12323020420'),('e2108f1d-6c12-45ba-b7af-499537a67419','12323020420','测试提交附件md','贺文杰','ORPHAN_EDUCATION_ASSISTANCE','测试','2025-12-16 00:00:00','2025-12-18 00:00:00',0.5,'FailReview','没有附件','2025-12-17 12:07:16','2025-12-17 12:29:59','12323020420'),('e8704e58-b9cc-430c-953a-7109249dc6c9','12323020420','测试5','12323020402','COMMUNITY_SERVICE','测试','2025-12-16 00:00:00','2025-12-18 00:00:00',0.5,'FailReview','测试','2025-12-17 12:09:38','2025-12-17 12:29:53','12323020420'),('ef3c94b4-b63b-4742-8477-f35feac4679e','12323020420','测试','12323020420','COMMUNITY_SERVICE','测试','2025-12-16 00:00:00','2025-12-18 00:00:00',0.5,'UnderReview',NULL,'2025-12-17 12:37:29',NULL,NULL),('f840a15c-88e9-4db5-9300-e97fd3a154d4','12323020420','测试申请时长','黄智哲','COMMUNITY_SERVICE','测试申请时长','2025-12-09 00:00:00','2025-12-19 00:00:00',3,'ActivityEnded',NULL,'2025-12-16 21:00:30','2025-12-16 21:13:41','12323020420');
/*!40000 ALTER TABLE `personal_hour_requests` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `suggestions`
--

DROP TABLE IF EXISTS `suggestions`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `suggestions` (
  `id` varchar(64) NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` text NOT NULL,
  `student_no` varchar(20) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `reply_content` text,
  `reply_time` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `suggestions`
--

LOCK TABLES `suggestions` WRITE;
/*!40000 ALTER TABLE `suggestions` DISABLE KEYS */;
INSERT INTO `suggestions` VALUES ('f0763368-35f7-40df-8c7d-226e723ccf2c','UI难看','UI难看','12323020421','REPLIED','修改中','2025-12-17 14:01:51','2025-12-17 13:38:49');
/*!40000 ALTER TABLE `suggestions` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `student_no` varchar(20) NOT NULL,
  `username` varchar(80) NOT NULL,
  `password` varchar(200) NOT NULL DEFAULT 'arookieofc',
  `role` varchar(20) NOT NULL,
  `created_at` datetime NOT NULL,
  `total_hours` double NOT NULL DEFAULT '0',
  `clazz` varchar(50) DEFAULT NULL COMMENT '班级',
  `grade` varchar(20) DEFAULT NULL COMMENT '年级',
  `college` varchar(100) DEFAULT NULL COMMENT '学院',
  PRIMARY KEY (`student_no`),
  KEY `idx_users_grade` (`grade`),
  KEY `idx_users_college` (`college`),
  KEY `idx_users_clazz` (`clazz`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES ('12323020334','高永旗','arookieofc','user','2025-11-27 19:03:55',20,'123230203','2023','两江人工智能学院'),('12323020406','罗正','arookieofc','user','2025-11-24 16:18:26',24,'123230204','2023','两江人工智能学院'),('12323020420','黄智哲','arookieofc','superAdmin','2025-11-25 21:49:45',27.5,'123230204','2023','两江人工智能学院'),('12323020421','贺文杰','arookieofc','functionary','2025-11-25 21:50:28',0,'123230204','2023','两江人工智能学院'),('12323020422','龚文杰','arookieofc','admin','2025-11-25 21:50:57',0,'123230204','2023','两江人工智能学院'),('12423020320','杨雪莲','arookieofc','admin','2025-12-15 23:53:58',24,'124230203','2024','两江人工智能学院');
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-01-06 18:49:18
