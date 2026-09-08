-- phpMyAdmin SQL Dump
-- version 5.2.3
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1:3306
-- Generation Time: Sep 06, 2026 at 10:31 AM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `ecommerceweb`
--

-- --------------------------------------------------------

--
-- Table structure for table `tbl_advertisements`
--

CREATE TABLE `tbl_advertisements` (
  `ad_id` int(11) NOT NULL,
  `advertiser_user_id` int(11) NOT NULL,
  `title` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `ad_type` enum('banner','popup','featured_listing') NOT NULL DEFAULT 'banner',
  `target_url` varchar(255) DEFAULT NULL,
  `image_file` varchar(255) DEFAULT NULL,
  `start_date` datetime NOT NULL,
  `end_date` datetime NOT NULL,
  `price_paid` decimal(10,2) DEFAULT NULL,
  `status` enum('active','inactive','pending_approval','rejected','expired') NOT NULL DEFAULT 'pending_approval',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_auctions`
--

CREATE TABLE `tbl_auctions` (
  `auction_id` int(11) NOT NULL,
  `seller_user_id` int(11) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `starting_bid` decimal(10,2) NOT NULL,
  `current_bid` decimal(10,2) DEFAULT NULL,
  `highest_bidder_user_id` int(11) DEFAULT NULL,
  `main_photo` varchar(255) DEFAULT NULL,
  `other_photos` text DEFAULT NULL,
  `start_time` datetime NOT NULL,
  `end_time` datetime NOT NULL,
  `status` enum('active','completed','cancelled','pending_approval','rejected') NOT NULL DEFAULT 'pending_approval',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_auction_bids`
--

CREATE TABLE `tbl_auction_bids` (
  `bid_id` int(11) NOT NULL,
  `auction_id` int(11) NOT NULL,
  `bidder_user_id` int(11) NOT NULL,
  `bid_amount` decimal(10,2) NOT NULL,
  `bid_time` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_businesses`
--

CREATE TABLE `tbl_businesses` (
  `business_id` int(11) NOT NULL,
  `owner_user_id` int(11) NOT NULL,
  `business_name` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `address` varchar(255) NOT NULL,
  `city` varchar(100) NOT NULL,
  `nid_number` varchar(50) DEFAULT NULL,
  `nid_front_doc` varchar(255) NOT NULL,
  `nid_back_doc` varchar(255) NOT NULL,
  `nid_document` varchar(255) DEFAULT NULL,
  `face_capture` varchar(255) DEFAULT NULL,
  `state` varchar(100) DEFAULT NULL,
  `zip` varchar(20) DEFAULT NULL,
  `phone` varchar(50) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `trade_license_number` varchar(100) DEFAULT NULL,
  `trade_license_photo` varchar(255) DEFAULT NULL,
  `tin_certificate_photo` varchar(255) DEFAULT NULL,
  `other_organization_docs` text DEFAULT NULL,
  `website` varchar(255) DEFAULT NULL,
  `latitude` decimal(10,8) DEFAULT NULL,
  `longitude` decimal(11,8) DEFAULT NULL,
  `logo` varchar(255) DEFAULT NULL,
  `banner_photo` varchar(255) DEFAULT NULL,
  `verification_status` enum('pending','approved','rejected') NOT NULL DEFAULT 'pending',
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_coin_transactions`
--

CREATE TABLE `tbl_coin_transactions` (
  `transaction_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `transaction_type` enum('credit','debit') NOT NULL,
  `amount` decimal(15,2) NOT NULL,
  `description` text DEFAULT NULL,
  `source_id` int(11) DEFAULT NULL,
  `transaction_date` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_color`
--

CREATE TABLE `tbl_color` (
  `color_id` int(11) NOT NULL,
  `color_name` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_color`
--

INSERT INTO `tbl_color` (`color_id`, `color_name`) VALUES
(1, 'Red'),
(2, 'Black'),
(3, 'Blue'),
(4, 'Yellow'),
(5, 'Green'),
(6, 'White'),
(7, 'Orange'),
(8, 'Brown'),
(9, 'Tan'),
(10, 'Pink'),
(11, 'Mixed'),
(12, 'Lightblue'),
(13, 'Violet'),
(14, 'Light Purple'),
(15, 'Salmon'),
(16, 'Gold'),
(17, 'Gray'),
(18, 'Ash'),
(19, 'Maroon'),
(20, 'Silver'),
(21, 'Dark Clay'),
(22, 'Cognac'),
(23, 'Coffee'),
(24, 'Charcoal'),
(25, 'Navy'),
(26, 'Fuchsia'),
(27, 'Olive'),
(28, 'Burgundy'),
(29, 'Midnight Blue'),
(1, 'Red'),
(2, 'Black'),
(3, 'Blue'),
(4, 'Yellow'),
(5, 'Green'),
(6, 'White'),
(7, 'Orange'),
(8, 'Brown'),
(9, 'Tan'),
(10, 'Pink'),
(11, 'Mixed'),
(12, 'Lightblue'),
(13, 'Violet'),
(14, 'Light Purple'),
(15, 'Salmon'),
(16, 'Gold'),
(17, 'Gray'),
(18, 'Ash'),
(19, 'Maroon'),
(20, 'Silver'),
(21, 'Dark Clay'),
(22, 'Cognac'),
(23, 'Coffee'),
(24, 'Charcoal'),
(25, 'Navy'),
(26, 'Fuchsia'),
(27, 'Olive'),
(28, 'Burgundy'),
(29, 'Midnight Blue');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_country`
--

CREATE TABLE `tbl_country` (
  `country_id` int(11) NOT NULL,
  `country_name` varchar(100) NOT NULL DEFAULT ''
) ENGINE=MyISAM DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Dumping data for table `tbl_country`
--

INSERT INTO `tbl_country` (`country_id`, `country_name`) VALUES
(1, 'Afghanistan'),
(2, 'Albania'),
(3, 'Algeria'),
(4, 'American Samoa'),
(5, 'Andorra'),
(6, 'Angola'),
(7, 'Anguilla'),
(8, 'Antarctica'),
(9, 'Antigua and Barbuda'),
(10, 'Argentina'),
(11, 'Armenia'),
(12, 'Aruba'),
(13, 'Australia'),
(14, 'Austria'),
(15, 'Azerbaijan'),
(16, 'Bahamas'),
(17, 'Bahrain'),
(18, 'Bangladesh'),
(19, 'Barbados'),
(20, 'Belarus'),
(21, 'Belgium'),
(22, 'Belize'),
(23, 'Benin'),
(24, 'Bermuda'),
(25, 'Bhutan'),
(26, 'Bolivia'),
(27, 'Bosnia and Herzegovina'),
(28, 'Botswana'),
(29, 'Bouvet Island'),
(30, 'Brazil'),
(31, 'British Indian Ocean Territory'),
(32, 'Brunei Darussalam'),
(33, 'Bulgaria'),
(34, 'Burkina Faso'),
(35, 'Burundi'),
(36, 'Cambodia'),
(37, 'Cameroon'),
(38, 'Canada'),
(39, 'Cape Verde'),
(40, 'Cayman Islands'),
(41, 'Central African Republic'),
(42, 'Chad'),
(43, 'Chile'),
(44, 'China'),
(45, 'Christmas Island'),
(46, 'Cocos (Keeling) Islands'),
(47, 'Colombia'),
(48, 'Comoros'),
(49, 'Congo'),
(50, 'Cook Islands'),
(51, 'Costa Rica'),
(52, 'Croatia (Hrvatska)'),
(53, 'Cuba'),
(54, 'Cyprus'),
(55, 'Czech Republic'),
(56, 'Denmark'),
(57, 'Djibouti'),
(58, 'Dominica'),
(59, 'Dominican Republic'),
(60, 'East Timor'),
(61, 'Ecuador'),
(62, 'Egypt'),
(63, 'El Salvador'),
(64, 'Equatorial Guinea'),
(65, 'Eritrea'),
(66, 'Estonia'),
(67, 'Ethiopia'),
(68, 'Falkland Islands (Malvinas)'),
(69, 'Faroe Islands'),
(70, 'Fiji'),
(71, 'Finland'),
(72, 'France'),
(73, 'France, Metropolitan'),
(74, 'French Guiana'),
(75, 'French Polynesia'),
(76, 'French Southern Territories'),
(77, 'Gabon'),
(78, 'Gambia'),
(79, 'Georgia'),
(80, 'Germany'),
(81, 'Ghana'),
(82, 'Gibraltar'),
(83, 'Guernsey'),
(84, 'Greece'),
(85, 'Greenland'),
(86, 'Grenada'),
(87, 'Guadeloupe'),
(88, 'Guam'),
(89, 'Guatemala'),
(90, 'Guinea'),
(91, 'Guinea-Bissau'),
(92, 'Guyana'),
(93, 'Haiti'),
(94, 'Heard and Mc Donald Islands'),
(95, 'Honduras'),
(96, 'Hong Kong'),
(97, 'Hungary'),
(98, 'Iceland'),
(99, 'India'),
(100, 'Isle of Man'),
(101, 'Indonesia'),
(102, 'Iran (Islamic Republic of)'),
(103, 'Iraq'),
(104, 'Ireland'),
(105, 'Israel'),
(106, 'Italy'),
(107, 'Ivory Coast'),
(108, 'Jersey'),
(109, 'Jamaica'),
(110, 'Japan'),
(111, 'Jordan'),
(112, 'Kazakhstan'),
(113, 'Kenya'),
(114, 'Kiribati'),
(115, 'Korea, Democratic People\'s Republic of'),
(116, 'Korea, Republic of'),
(117, 'Kosovo'),
(118, 'Kuwait'),
(119, 'Kyrgyzstan'),
(120, 'Lao People\'s Democratic Republic'),
(121, 'Latvia'),
(122, 'Lebanon'),
(123, 'Lesotho'),
(124, 'Liberia'),
(125, 'Libyan Arab Jamahiriya'),
(126, 'Liechtenstein'),
(127, 'Lithuania'),
(128, 'Luxembourg'),
(129, 'Macau'),
(130, 'Macedonia'),
(131, 'Madagascar'),
(132, 'Malawi'),
(133, 'Malaysia'),
(134, 'Maldives'),
(135, 'Mali'),
(136, 'Malta'),
(137, 'Marshall Islands'),
(138, 'Martinique'),
(139, 'Mauritania'),
(140, 'Mauritius'),
(141, 'Mayotte'),
(142, 'Mexico'),
(143, 'Micronesia, Federated States of'),
(144, 'Moldova, Republic of'),
(145, 'Monaco'),
(146, 'Mongolia'),
(147, 'Montenegro'),
(148, 'Montserrat'),
(149, 'Morocco'),
(150, 'Mozambique'),
(151, 'Myanmar'),
(152, 'Namibia'),
(153, 'Nauru'),
(154, 'Nepal'),
(155, 'Netherlands'),
(156, 'Netherlands Antilles'),
(157, 'New Caledonia'),
(158, 'New Zealand'),
(159, 'Nicaragua'),
(160, 'Niger'),
(161, 'Nigeria'),
(162, 'Niue'),
(163, 'Norfolk Island'),
(164, 'Northern Mariana Islands'),
(165, 'Norway'),
(166, 'Oman'),
(167, 'Pakistan'),
(168, 'Palau'),
(169, 'Palestine'),
(170, 'Panama'),
(171, 'Papua New Guinea'),
(172, 'Paraguay'),
(173, 'Peru'),
(174, 'Philippines'),
(175, 'Pitcairn'),
(176, 'Poland'),
(177, 'Portugal'),
(178, 'Puerto Rico'),
(179, 'Qatar'),
(180, 'Reunion'),
(181, 'Romania'),
(182, 'Russian Federation'),
(183, 'Rwanda'),
(184, 'Saint Kitts and Nevis'),
(185, 'Saint Lucia'),
(186, 'Saint Vincent and the Grenadines'),
(187, 'Samoa'),
(188, 'San Marino'),
(189, 'Sao Tome and Principe'),
(190, 'Saudi Arabia'),
(191, 'Senegal'),
(192, 'Serbia'),
(193, 'Seychelles'),
(194, 'Sierra Leone'),
(195, 'Singapore'),
(196, 'Slovakia'),
(197, 'Slovenia'),
(198, 'Solomon Islands'),
(199, 'Somalia'),
(200, 'South Africa'),
(201, 'South Georgia South Sandwich Islands'),
(202, 'Spain'),
(203, 'Sri Lanka'),
(204, 'St. Helena'),
(205, 'St. Pierre and Miquelon'),
(206, 'Sudan'),
(207, 'Suriname'),
(208, 'Svalbard and Jan Mayen Islands'),
(209, 'Swaziland'),
(210, 'Sweden'),
(211, 'Switzerland'),
(212, 'Syrian Arab Republic'),
(213, 'Taiwan'),
(214, 'Tajikistan'),
(215, 'Tanzania, United Republic of'),
(216, 'Thailand'),
(217, 'Togo'),
(218, 'Tokelau'),
(219, 'Tonga'),
(220, 'Trinidad and Tobago'),
(221, 'Tunisia'),
(222, 'Turkey'),
(223, 'Turkmenistan'),
(224, 'Turks and Caicos Islands'),
(225, 'Tuvalu'),
(226, 'Uganda'),
(227, 'Ukraine'),
(228, 'United Arab Emirates'),
(229, 'United Kingdom'),
(230, 'United States'),
(231, 'United States minor outlying islands'),
(232, 'Uruguay'),
(233, 'Uzbekistan'),
(234, 'Vanuatu'),
(235, 'Vatican City State'),
(236, 'Venezuela'),
(237, 'Vietnam'),
(238, 'Virgin Islands (British)'),
(239, 'Virgin Islands (U.S.)'),
(240, 'Wallis and Futuna Islands'),
(241, 'Western Sahara'),
(242, 'Yemen'),
(243, 'Zaire'),
(244, 'Zambia'),
(245, 'Zimbabwe'),
(1, 'Afghanistan'),
(2, 'Albania'),
(3, 'Algeria'),
(4, 'American Samoa'),
(5, 'Andorra'),
(6, 'Angola'),
(7, 'Anguilla'),
(8, 'Antarctica'),
(9, 'Antigua and Barbuda'),
(10, 'Argentina'),
(11, 'Armenia'),
(12, 'Aruba'),
(13, 'Australia'),
(14, 'Austria'),
(15, 'Azerbaijan'),
(16, 'Bahamas'),
(17, 'Bahrain'),
(18, 'Bangladesh'),
(19, 'Barbados'),
(20, 'Belarus'),
(21, 'Belgium'),
(22, 'Belize'),
(23, 'Benin'),
(24, 'Bermuda'),
(25, 'Bhutan'),
(26, 'Bolivia'),
(27, 'Bosnia and Herzegovina'),
(28, 'Botswana'),
(29, 'Bouvet Island'),
(30, 'Brazil'),
(31, 'British Indian Ocean Territory'),
(32, 'Brunei Darussalam'),
(33, 'Bulgaria'),
(34, 'Burkina Faso'),
(35, 'Burundi'),
(36, 'Cambodia'),
(37, 'Cameroon'),
(38, 'Canada'),
(39, 'Cape Verde'),
(40, 'Cayman Islands'),
(41, 'Central African Republic'),
(42, 'Chad'),
(43, 'Chile'),
(44, 'China'),
(45, 'Christmas Island'),
(46, 'Cocos (Keeling) Islands'),
(47, 'Colombia'),
(48, 'Comoros'),
(49, 'Congo'),
(50, 'Cook Islands'),
(51, 'Costa Rica'),
(52, 'Croatia (Hrvatska)'),
(53, 'Cuba'),
(54, 'Cyprus'),
(55, 'Czech Republic'),
(56, 'Denmark'),
(57, 'Djibouti'),
(58, 'Dominica'),
(59, 'Dominican Republic'),
(60, 'East Timor'),
(61, 'Ecuador'),
(62, 'Egypt'),
(63, 'El Salvador'),
(64, 'Equatorial Guinea'),
(65, 'Eritrea'),
(66, 'Estonia'),
(67, 'Ethiopia'),
(68, 'Falkland Islands (Malvinas)'),
(69, 'Faroe Islands'),
(70, 'Fiji'),
(71, 'Finland'),
(72, 'France'),
(73, 'France, Metropolitan'),
(74, 'French Guiana'),
(75, 'French Polynesia'),
(76, 'French Southern Territories'),
(77, 'Gabon'),
(78, 'Gambia'),
(79, 'Georgia'),
(80, 'Germany'),
(81, 'Ghana'),
(82, 'Gibraltar'),
(83, 'Guernsey'),
(84, 'Greece'),
(85, 'Greenland'),
(86, 'Grenada'),
(87, 'Guadeloupe'),
(88, 'Guam'),
(89, 'Guatemala'),
(90, 'Guinea'),
(91, 'Guinea-Bissau'),
(92, 'Guyana'),
(93, 'Haiti'),
(94, 'Heard and Mc Donald Islands'),
(95, 'Honduras'),
(96, 'Hong Kong'),
(97, 'Hungary'),
(98, 'Iceland'),
(99, 'India'),
(100, 'Isle of Man'),
(101, 'Indonesia'),
(102, 'Iran (Islamic Republic of)'),
(103, 'Iraq'),
(104, 'Ireland'),
(105, 'Israel'),
(106, 'Italy'),
(107, 'Ivory Coast'),
(108, 'Jersey'),
(109, 'Jamaica'),
(110, 'Japan'),
(111, 'Jordan'),
(112, 'Kazakhstan'),
(113, 'Kenya'),
(114, 'Kiribati'),
(115, 'Korea, Democratic People\'s Republic of'),
(116, 'Korea, Republic of'),
(117, 'Kosovo'),
(118, 'Kuwait'),
(119, 'Kyrgyzstan'),
(120, 'Lao People\'s Democratic Republic'),
(121, 'Latvia'),
(122, 'Lebanon'),
(123, 'Lesotho'),
(124, 'Liberia'),
(125, 'Libyan Arab Jamahiriya'),
(126, 'Liechtenstein'),
(127, 'Lithuania'),
(128, 'Luxembourg'),
(129, 'Macau'),
(130, 'Macedonia'),
(131, 'Madagascar'),
(132, 'Malawi'),
(133, 'Malaysia'),
(134, 'Maldives'),
(135, 'Mali'),
(136, 'Malta'),
(137, 'Marshall Islands'),
(138, 'Martinique'),
(139, 'Mauritania'),
(140, 'Mauritius'),
(141, 'Mayotte'),
(142, 'Mexico'),
(143, 'Micronesia, Federated States of'),
(144, 'Moldova, Republic of'),
(145, 'Monaco'),
(146, 'Mongolia'),
(147, 'Montenegro'),
(148, 'Montserrat'),
(149, 'Morocco'),
(150, 'Mozambique'),
(151, 'Myanmar'),
(152, 'Namibia'),
(153, 'Nauru'),
(154, 'Nepal'),
(155, 'Netherlands'),
(156, 'Netherlands Antilles'),
(157, 'New Caledonia'),
(158, 'New Zealand'),
(159, 'Nicaragua'),
(160, 'Niger'),
(161, 'Nigeria'),
(162, 'Niue'),
(163, 'Norfolk Island'),
(164, 'Northern Mariana Islands'),
(165, 'Norway'),
(166, 'Oman'),
(167, 'Pakistan'),
(168, 'Palau'),
(169, 'Palestine'),
(170, 'Panama'),
(171, 'Papua New Guinea'),
(172, 'Paraguay'),
(173, 'Peru'),
(174, 'Philippines'),
(175, 'Pitcairn'),
(176, 'Poland'),
(177, 'Portugal'),
(178, 'Puerto Rico'),
(179, 'Qatar'),
(180, 'Reunion'),
(181, 'Romania'),
(182, 'Russian Federation'),
(183, 'Rwanda'),
(184, 'Saint Kitts and Nevis'),
(185, 'Saint Lucia'),
(186, 'Saint Vincent and the Grenadines'),
(187, 'Samoa'),
(188, 'San Marino'),
(189, 'Sao Tome and Principe'),
(190, 'Saudi Arabia'),
(191, 'Senegal'),
(192, 'Serbia'),
(193, 'Seychelles'),
(194, 'Sierra Leone'),
(195, 'Singapore'),
(196, 'Slovakia'),
(197, 'Slovenia'),
(198, 'Solomon Islands'),
(199, 'Somalia'),
(200, 'South Africa'),
(201, 'South Georgia South Sandwich Islands'),
(202, 'Spain'),
(203, 'Sri Lanka'),
(204, 'St. Helena'),
(205, 'St. Pierre and Miquelon'),
(206, 'Sudan'),
(207, 'Suriname'),
(208, 'Svalbard and Jan Mayen Islands'),
(209, 'Swaziland'),
(210, 'Sweden'),
(211, 'Switzerland'),
(212, 'Syrian Arab Republic'),
(213, 'Taiwan'),
(214, 'Tajikistan'),
(215, 'Tanzania, United Republic of'),
(216, 'Thailand'),
(217, 'Togo'),
(218, 'Tokelau'),
(219, 'Tonga'),
(220, 'Trinidad and Tobago'),
(221, 'Tunisia'),
(222, 'Turkey'),
(223, 'Turkmenistan'),
(224, 'Turks and Caicos Islands'),
(225, 'Tuvalu'),
(226, 'Uganda'),
(227, 'Ukraine'),
(228, 'United Arab Emirates'),
(229, 'United Kingdom'),
(230, 'United States'),
(231, 'United States minor outlying islands'),
(232, 'Uruguay'),
(233, 'Uzbekistan'),
(234, 'Vanuatu'),
(235, 'Vatican City State'),
(236, 'Venezuela'),
(237, 'Vietnam'),
(238, 'Virgin Islands (British)'),
(239, 'Virgin Islands (U.S.)'),
(240, 'Wallis and Futuna Islands'),
(241, 'Western Sahara'),
(242, 'Yemen'),
(243, 'Zaire'),
(244, 'Zambia'),
(245, 'Zimbabwe');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_coupon`
--

CREATE TABLE `tbl_coupon` (
  `coupon_id` int(11) NOT NULL,
  `coupon_code` varchar(50) NOT NULL,
  `discount_type` enum('percentage','fixed') NOT NULL DEFAULT 'percentage',
  `discount_value` decimal(10,2) NOT NULL,
  `minimum_order` decimal(10,2) NOT NULL DEFAULT 0.00,
  `usage_limit` int(11) NOT NULL DEFAULT 0,
  `used_count` int(11) NOT NULL DEFAULT 0,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `status` enum('active','inactive') NOT NULL DEFAULT 'active'
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

--
-- Dumping data for table `tbl_coupon`
--

INSERT INTO `tbl_coupon` (`coupon_id`, `coupon_code`, `discount_type`, `discount_value`, `minimum_order`, `usage_limit`, `used_count`, `start_date`, `end_date`, `status`) VALUES
(1, '6', 'fixed', 666.00, 0.00, 0, 6, '2025-06-07', '2025-10-12', 'active');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_customer`
--

CREATE TABLE `tbl_customer` (
  `cust_id` int(11) NOT NULL,
  `cust_name` varchar(100) NOT NULL,
  `cust_cname` varchar(100) NOT NULL,
  `cust_email` varchar(100) NOT NULL,
  `cust_phone` varchar(50) NOT NULL,
  `cust_country` int(11) NOT NULL,
  `cust_address` text NOT NULL,
  `cust_city` varchar(100) NOT NULL,
  `cust_state` varchar(100) NOT NULL,
  `cust_zip` varchar(30) NOT NULL,
  `cust_b_name` varchar(100) NOT NULL,
  `cust_b_cname` varchar(100) NOT NULL,
  `cust_b_phone` varchar(50) NOT NULL,
  `cust_b_country` int(11) NOT NULL,
  `cust_b_address` text NOT NULL,
  `cust_b_city` varchar(100) NOT NULL,
  `cust_b_state` varchar(100) NOT NULL,
  `cust_b_zip` varchar(30) NOT NULL,
  `cust_s_name` varchar(100) NOT NULL,
  `cust_s_cname` varchar(100) NOT NULL,
  `cust_s_phone` varchar(50) NOT NULL,
  `cust_s_country` int(11) NOT NULL,
  `cust_s_address` text NOT NULL,
  `cust_s_city` varchar(100) NOT NULL,
  `cust_s_state` varchar(100) NOT NULL,
  `cust_s_zip` varchar(30) NOT NULL,
  `cust_password` varchar(255) NOT NULL,
  `cust_coin_balance` decimal(15,2) NOT NULL DEFAULT 0.00,
  `cust_token` varchar(255) NOT NULL,
  `cust_datetime` varchar(100) NOT NULL,
  `cust_timestamp` varchar(100) NOT NULL,
  `cust_status` int(1) NOT NULL,
  `role_type` varchar(50) DEFAULT 'customer',
  `is_verified` tinyint(1) DEFAULT 0,
  `trust_score` int(11) DEFAULT 50,
  `current_lat` decimal(10,8) DEFAULT NULL,
  `current_lng` decimal(11,8) DEFAULT NULL,
  `wallet_balance` decimal(15,2) DEFAULT 0.00,
  `identity_doc_path` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_customer`
--

INSERT INTO `tbl_customer` (`cust_id`, `cust_name`, `cust_cname`, `cust_email`, `cust_phone`, `cust_country`, `cust_address`, `cust_city`, `cust_state`, `cust_zip`, `cust_b_name`, `cust_b_cname`, `cust_b_phone`, `cust_b_country`, `cust_b_address`, `cust_b_city`, `cust_b_state`, `cust_b_zip`, `cust_s_name`, `cust_s_cname`, `cust_s_phone`, `cust_s_country`, `cust_s_address`, `cust_s_city`, `cust_s_state`, `cust_s_zip`, `cust_password`, `cust_coin_balance`, `cust_token`, `cust_datetime`, `cust_timestamp`, `cust_status`, `role_type`, `is_verified`, `trust_score`, `current_lat`, `current_lng`, `wallet_balance`, `identity_doc_path`) VALUES
(1, 'Liam Moore', 'WV Company', 'liam@mail.com', '7458965410', 230, '788 Cottonwood Lane', 'Nashville', 'TN', '37072', '', '', '', 0, '', '', '', '', '', '', '', 0, '', '', '', '', '5f4dcc3b5aa765d61d8327deb882cf99', 0.00, '0081e99a29cacd4b553db15c5c5c047e', '2022-03-17 11:09:34', '1647544174', 1, 'customer', 0, 50, NULL, NULL, 0.00, NULL),
(2, 'Chad N. Carney', 'none', 'chad@mail.com', '4785690000', 230, '469 Diamond Street', 'Charlotte', 'NC', '28808', 'Chad N. Carney', 'none', '7477474440', 230, '469 Diamond Street', 'Charlotte', 'NC', '28808', 'Chad N. Carney', 'none', '7477474440', 230, '469 Diamond Street', 'Charlotte', 'NC', '28808', '5f4dcc3b5aa765d61d8327deb882cf99', 0.00, 'ca87666426f4bc5c5128a96dabfecefb', '2022-03-17 11:15:26', '1647544526', 1, 'customer', 0, 50, NULL, NULL, 0.00, NULL),
(3, 'Jean Collins', 'none', 'jean@mail.com', '1478523698', 230, '1508 Crosswind Drive', 'Owensboro', 'KY', '13040', 'Jean Collins', 'none', '1478523698', 230, '1508 Crosswind Drive', 'Owensboro', 'KY', '13040', 'Jean Collins', 'none', '1478523698', 230, '1508 Crosswind Drive', 'Owensboro', 'KY', '13040', '5f4dcc3b5aa765d61d8327deb882cf99', 0.00, '6b3439bf95644a36a1ed92bef374ebb7', '2022-03-20 10:29:39', '1647797379', 1, 'customer', 0, 50, NULL, NULL, 0.00, NULL),
(4, 'Annie Young', 'XYZ Company', 'annie@mail.com', '7770001144', 230, '79 Burwell Heights Road', 'Beaumont', 'TX', '77400', '', '', '', 0, '', '', '', '', '', '', '', 0, '', '', '', '', '5f4dcc3b5aa765d61d8327deb882cf99', 0.00, 'fc8f07537cdd6b3f89eb94f1cad78060', '2022-03-20 10:31:35', '1647797495', 1, 'customer', 0, 50, NULL, NULL, 0.00, NULL),
(5, 'Matthew Morales', 'ABC Company', 'matthew@mail.com', '7896587450', 230, '81 Felosa Drive', 'Mira Loma', 'CA', '91002', 'Matthew Morales', 'ABC Company', '7896587450', 230, '81 Felosa Drive', 'Mira Loma', 'CA', '91002', 'Matthew Morales', 'ABC Company', '7896587450', 230, '81 Felosa Drive', 'Mira Loma', 'CA', '91002', '5f4dcc3b5aa765d61d8327deb882cf99', 0.00, 'c391105908fe01a636bfa5fc39eed33d', '2022-03-20 10:33:15', '1647797595', 1, 'customer', 0, 50, NULL, NULL, 0.00, NULL),
(6, 'August F. Freels', 'none', 'august@mail.com', '1478547850', 230, '96 Johnny Lane', 'Milwaukee', 'WI', '55550', 'August F. Freels', 'none', '1478547850', 230, '96 Johnny Lane', 'Milwaukee', 'WI', '55550', 'August F. Freels', 'none', '1478547850', 230, '96 Johnny Lane', 'Milwaukee', 'WI', '55550', '5f4dcc3b5aa765d61d8327deb882cf99', 0.00, 'decc1fc2c5dd9935df82c0233002ce66', '2022-03-20 10:34:08', '1647797648', 1, 'customer', 0, 50, NULL, NULL, 0.00, NULL),
(7, 'Carl M. Dineen', 'none', 'carl@mail.com', '789878987', 230, '77 Lyndon Street', 'Kutztown', 'PA', '19855', '', '', '', 0, '', '', '', '', '', '', '', 0, '', '', '', '', '5f4dcc3b5aa765d61d8327deb882cf99', 0.00, 'c79bac688e70cc9665a2164c57ec172c', '2022-03-20 10:35:02', '1647797702', 1, 'customer', 0, 50, NULL, NULL, 0.00, NULL),
(8, 'Benjamin B. Louque', 'none', 'benjamin@mail.com', '7777889955', 230, '32 Bridge Street', 'Tulsa', 'OK', '74220', '', '', '', 0, '', '', '', '', '', '', '', 0, '', '', '', '', '5f4dcc3b5aa765d61d8327deb882cf99', 0.00, '5a0e096368f9669508af7b7203382b07', '2022-03-20 10:36:31', '1647797791', 1, 'customer', 0, 50, NULL, NULL, 0.00, NULL),
(9, 'Joe K. Richardson', 'none', 'joe@mail.com', '4444445555', 230, '17 Derek Drive', 'Youngstown', 'OH', '44500', '', '', '', 0, '', '', '', '', '', '', '', 0, '', '', '', '', '5f4dcc3b5aa765d61d8327deb882cf99', 0.00, 'e74ac0178d7833988d4b1625c42ba26e', '2022-03-20 10:37:18', '1647797838', 1, 'customer', 0, 50, NULL, NULL, 0.00, NULL),
(10, 'Will Williams', 'Test Company', 'williams@mail.com', '7410000000', 230, '39 Marcus Street', 'Anniston', 'AL', '37207', 'Will Williams', 'Test Company', '7410000000', 230, '39 Marcus Street', 'Anniston', 'AL', '37207', 'Will Williams', 'Test Company', '7410000000', 230, '39 Marcus Street', 'Anniston', 'AL', '37207', '5f4dcc3b5aa765d61d8327deb882cf99', 0.00, '941c9265fb920f691cf01b12a15f80f8', '2022-03-20 11:15:59', '1647800159', 1, 'customer', 0, 50, NULL, NULL, 0.00, NULL),
(24, 'Joy Saha', '', 'jsaha3741@gmail.com', '01735342839', 18, 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'CUMILLA', 'Cfg', '3700', 'Joy Saha', 'ytrfhgfh', '01735342839', 18, 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'CUMILLA', 'Cfg', '3700', 'Joy Saha', 'tyyt', '01735342839', 18, 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'CUMILLA', 'Cfg', '3700', '$2y$10$EN89MGW6KGSQYVMsoVcl6.mYQX6yG0wo1kCTessRtOBeMXqzsMYKi', 0.00, '', '2026-01-21 02:51:27', '', 1, 'customer', 0, 50, NULL, NULL, 0.00, NULL);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_customer_carts`
--

CREATE TABLE `tbl_customer_carts` (
  `cart_id` int(11) NOT NULL,
  `customer_id` int(11) NOT NULL,
  `product_id` int(11) NOT NULL,
  `size_id` int(11) DEFAULT NULL,
  `size_name` varchar(255) DEFAULT '',
  `color_id` int(11) DEFAULT NULL,
  `color_name` varchar(255) DEFAULT '',
  `quantity` int(11) NOT NULL DEFAULT 1,
  `price_at_add` decimal(10,2) NOT NULL DEFAULT 0.00,
  `product_name` varchar(255) DEFAULT '',
  `product_photo` varchar(255) DEFAULT NULL,
  `added_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `updated_at` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_customer_carts`
--

INSERT INTO `tbl_customer_carts` (`cart_id`, `customer_id`, `product_id`, `size_id`, `size_name`, `color_id`, `color_name`, `quantity`, `price_at_add`, `product_name`, `product_photo`, `added_at`, `updated_at`) VALUES
(18, 24, 87, 29, '12 Months', 3, 'Blue', 1, 37.00, 'Truck Boys Pajamas Toddler Sleepwear Clothes', 'product-featured-87.jpg', '2026-01-21 12:56:18', '2026-01-21 12:56:18'),
(19, 24, 102, 42, '14 Plus', 2, 'Black', 2, 169.00, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', 'product-featured-102.jpg', '2026-01-21 13:16:43', '2026-01-21 13:16:46');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_customer_message`
--

CREATE TABLE `tbl_customer_message` (
  `customer_message_id` int(11) NOT NULL,
  `subject` varchar(255) NOT NULL,
  `message` text NOT NULL,
  `order_detail` text NOT NULL,
  `cust_id` int(11) NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_customer_message`
--

INSERT INTO `tbl_customer_message` (`customer_message_id`, `subject`, `message`, `order_detail`, `cust_id`) VALUES
(0, 'yt', '5yr', '\nCustomer Name: Joy Saha<br>\nCustomer Email: s@1.com<br>\nPayment Method: PayPal<br>\nPayment Date: 2025-06-06 06:18:34<br>\nPayment Details: <br>\nTransaction Id: <br>\n        		<br>\nPaid Amount: 379<br>\nPayment Status: Pending<br>\nShipping Status: Pending<br>\nPayment Id: 1749215914<br>\n            ', 0),
(0, 'yt', '5yr', '\nCustomer Name: Joy Saha<br>\nCustomer Email: s@1.com<br>\nPayment Method: PayPal<br>\nPayment Date: 2025-06-06 06:18:34<br>\nPayment Details: <br>\nTransaction Id: <br>\n        		<br>\nPaid Amount: 379<br>\nPayment Status: Pending<br>\nShipping Status: Pending<br>\nPayment Id: 1749215914<br>\n            ', 0),
(0, 'yt', '5yr', '\nCustomer Name: Joy Saha<br>\nCustomer Email: s@1.com<br>\nPayment Method: PayPal<br>\nPayment Date: 2025-06-06 06:18:34<br>\nPayment Details: <br>\nTransaction Id: <br>\n        		<br>\nPaid Amount: 379<br>\nPayment Status: Pending<br>\nShipping Status: Pending<br>\nPayment Id: 1749215914<br>\n            ', 0),
(0, 'yt', '5yr', '\nCustomer Name: Joy Saha<br>\nCustomer Email: s@1.com<br>\nPayment Method: PayPal<br>\nPayment Date: 2025-06-06 06:18:34<br>\nPayment Details: <br>\nTransaction Id: <br>\n        		<br>\nPaid Amount: 379<br>\nPayment Status: Pending<br>\nShipping Status: Pending<br>\nPayment Id: 1749215914<br>\n            ', 0);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_drivers`
--

CREATE TABLE `tbl_drivers` (
  `driver_id` int(11) NOT NULL,
  `user_id` int(11) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `phone` varchar(50) NOT NULL,
  `nid_number` varchar(50) DEFAULT NULL,
  `nid_photo_front` varchar(255) DEFAULT NULL,
  `nid_photo_back` varchar(255) DEFAULT NULL,
  `driving_license_number` varchar(100) DEFAULT NULL,
  `driving_license_photo` varchar(255) DEFAULT NULL,
  `vehicle_registration_photo` varchar(255) DEFAULT NULL,
  `license_number` varchar(100) NOT NULL,
  `vehicle_type` enum('rickshaw','cng','bike','car') NOT NULL,
  `current_latitude` decimal(10,8) DEFAULT NULL,
  `current_longitude` decimal(11,8) DEFAULT NULL,
  `status` enum('online','offline','on_trip') NOT NULL DEFAULT 'offline',
  `is_verified` tinyint(1) NOT NULL DEFAULT 0,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_emergency_contacts`
--

CREATE TABLE `tbl_emergency_contacts` (
  `contact_id` int(11) NOT NULL,
  `service_type` varchar(100) NOT NULL,
  `contact_number` varchar(50) NOT NULL,
  `region` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_end_category`
--

CREATE TABLE `tbl_end_category` (
  `ecat_id` int(11) NOT NULL,
  `ecat_name` varchar(255) NOT NULL,
  `mcat_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_end_category`
--

INSERT INTO `tbl_end_category` (`ecat_id`, `ecat_name`, `mcat_id`) VALUES
(1, 'Headwear ', 1),
(2, 'Sunglasses', 1),
(3, 'Watches', 1),
(4, 'Sandals', 2),
(5, 'Boots', 2),
(6, 'Tops', 3),
(7, 'T-Shirt', 3),
(8, 'Watches', 4),
(9, 'Sunglasses', 4),
(11, 'Sports Shoes', 2),
(12, 'Sandals', 6),
(13, 'Flat Shoes', 6),
(14, 'Hoodies', 7),
(15, 'Coats & Jackets', 7),
(16, 'Pants', 8),
(17, 'Jeans', 8),
(18, 'Joggers', 8),
(19, 'Shorts', 8),
(20, 'T-shirts', 9),
(21, 'Casual Shirts', 9),
(22, 'Formal Shirts', 9),
(23, 'Polo Shirts', 9),
(24, 'Vests', 9),
(25, 'Casual Shoes', 2),
(26, 'Boys', 10),
(27, 'Girls', 10),
(28, 'Boys', 11),
(29, 'Girls', 11),
(30, 'Boys', 12),
(31, 'Girls', 12),
(32, 'Dresses', 7),
(33, 'Tops', 7),
(34, 'T-Shirts & Vests', 7),
(35, 'Pants & Leggings', 7),
(36, 'Sportswear', 7),
(37, 'Plus Size Clothing', 7),
(38, 'Socks & Hosiery', 7),
(39, 'Fragrance', 3),
(40, 'Skincare', 3),
(41, 'Hair Care', 3),
(42, 'Jewellery', 4),
(43, 'Eyes Care', 3),
(44, 'Lips', 3),
(45, 'Face Care', 3),
(46, 'Gift Sets', 3),
(47, 'Scarves & Headwear', 4),
(48, 'Multipacks', 4),
(49, 'Other Accessories', 4),
(50, 'Pumps', 6),
(51, 'Sneakers', 6),
(52, 'Sports Shoes', 6),
(53, 'Boots', 6),
(54, 'Comfort Shoes', 6),
(55, 'Slippers & Casual Shoes', 6),
(56, 'Formal Shoes', 2),
(57, 'Belts', 1),
(58, 'Multipacks', 1),
(59, 'Other Accessories', 1),
(60, 'Bags', 4),
(61, 'Cell Phone and Accessories', 14),
(62, 'Headphones', 14),
(63, 'Security and Surveillance', 14),
(64, 'Television and Video', 14),
(65, 'GPS and Navigation', 14),
(66, 'Home Audio', 14),
(67, 'Computer Components', 15),
(68, 'Computers and Tablets', 15),
(69, 'Laptop Accessories', 15),
(70, 'Printer and Monitors', 15),
(71, 'External Components', 15),
(72, 'Networking Products', 15),
(73, 'Medical Supplies and Equipment', 16),
(74, 'Oral Care', 16),
(75, 'Vision Care', 16),
(76, 'Vitamins and Dietary Supplements', 16),
(77, 'Baby and Child Care', 17),
(78, 'Household Supplies', 17),
(79, 'Stationery and Gift Wrapping Supplies', 17),
(1, 'Headwear ', 1),
(2, 'Sunglasses', 1),
(3, 'Watches', 1),
(4, 'Sandals', 2),
(5, 'Boots', 2),
(6, 'Tops', 3),
(7, 'T-Shirt', 3),
(8, 'Watches', 4),
(9, 'Sunglasses', 4),
(11, 'Sports Shoes', 2),
(12, 'Sandals', 6),
(13, 'Flat Shoes', 6),
(14, 'Hoodies', 7),
(15, 'Coats & Jackets', 7),
(16, 'Pants', 8),
(17, 'Jeans', 8),
(18, 'Joggers', 8),
(19, 'Shorts', 8),
(20, 'T-shirts', 9),
(21, 'Casual Shirts', 9),
(22, 'Formal Shirts', 9),
(23, 'Polo Shirts', 9),
(24, 'Vests', 9),
(25, 'Casual Shoes', 2),
(26, 'Boys', 10),
(27, 'Girls', 10),
(28, 'Boys', 11),
(29, 'Girls', 11),
(30, 'Boys', 12),
(31, 'Girls', 12),
(32, 'Dresses', 7),
(33, 'Tops', 7),
(34, 'T-Shirts & Vests', 7),
(35, 'Pants & Leggings', 7),
(36, 'Sportswear', 7),
(37, 'Plus Size Clothing', 7),
(38, 'Socks & Hosiery', 7),
(39, 'Fragrance', 3),
(40, 'Skincare', 3),
(41, 'Hair Care', 3),
(42, 'Jewellery', 4),
(43, 'Eyes Care', 3),
(44, 'Lips', 3),
(45, 'Face Care', 3),
(46, 'Gift Sets', 3),
(47, 'Scarves & Headwear', 4),
(48, 'Multipacks', 4),
(49, 'Other Accessories', 4),
(50, 'Pumps', 6),
(51, 'Sneakers', 6),
(52, 'Sports Shoes', 6),
(53, 'Boots', 6),
(54, 'Comfort Shoes', 6),
(55, 'Slippers & Casual Shoes', 6),
(56, 'Formal Shoes', 2),
(57, 'Belts', 1),
(58, 'Multipacks', 1),
(59, 'Other Accessories', 1),
(60, 'Bags', 4),
(61, 'Cell Phone and Accessories', 14),
(62, 'Headphones', 14),
(63, 'Security and Surveillance', 14),
(64, 'Television and Video', 14),
(65, 'GPS and Navigation', 14),
(66, 'Home Audio', 14),
(67, 'Computer Components', 15),
(68, 'Computers and Tablets', 15),
(69, 'Laptop Accessories', 15),
(70, 'Printer and Monitors', 15),
(71, 'External Components', 15),
(72, 'Networking Products', 15),
(73, 'Medical Supplies and Equipment', 16),
(74, 'Oral Care', 16),
(75, 'Vision Care', 16),
(76, 'Vitamins and Dietary Supplements', 16),
(77, 'Baby and Child Care', 17),
(78, 'Household Supplies', 17),
(79, 'Stationery and Gift Wrapping Supplies', 17);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_faq`
--

CREATE TABLE `tbl_faq` (
  `faq_id` int(11) NOT NULL,
  `faq_title` varchar(255) NOT NULL,
  `faq_content` text NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_faq`
--

INSERT INTO `tbl_faq` (`faq_id`, `faq_title`, `faq_content`) VALUES
(1, 'How to find an item?', '<h3 class=\"checkout-complete-box font-bold txt16\" style=\"box-sizing: inherit; text-rendering: optimizeLegibility; margin: 0.2rem 0px 0.5rem; padding: 0px; line-height: 1.4; background-color: rgb(250, 250, 250);\"><font color=\"#222222\" face=\"opensans, Helvetica Neue, Helvetica, Helvetica, Arial, sans-serif\"><span style=\"font-size: 15.7143px;\">We have a wide range of fabulous products to choose from.</span></font></h3><h3 class=\"checkout-complete-box font-bold txt16\" style=\"box-sizing: inherit; text-rendering: optimizeLegibility; margin: 0.2rem 0px 0.5rem; padding: 0px; line-height: 1.4; background-color: rgb(250, 250, 250);\"><span style=\"font-size: 15.7143px; color: rgb(34, 34, 34); font-family: opensans, \"Helvetica Neue\", Helvetica, Helvetica, Arial, sans-serif;\">Tip 1: If you\'re looking for a specific product, use the keyword search box located at the top of the site. Simply type what you are looking for, and prepare to be amazed!</span></h3><h3 class=\"checkout-complete-box font-bold txt16\" style=\"box-sizing: inherit; text-rendering: optimizeLegibility; margin: 0.2rem 0px 0.5rem; padding: 0px; line-height: 1.4; background-color: rgb(250, 250, 250);\"><font color=\"#222222\" face=\"opensans, Helvetica Neue, Helvetica, Helvetica, Arial, sans-serif\"><span style=\"font-size: 15.7143px;\">Tip 2: If you want to explore a category of products, use the Shop Categories in the upper menu, and navigate through your favorite categories where we\'ll feature the best products in each.</span></font><br><br></h3>\r\n'),
(2, 'What is your return policy?', '<p><span style=\"color: rgb(10, 10, 10); font-family: opensans, &quot;Helvetica Neue&quot;, Helvetica, Helvetica, Arial, sans-serif; font-size: 14px; text-align: center;\">You have 15 days to make a refund request after your order has been delivered.</span><br></p>\r\n'),
(3, ' I received a defective/damaged item, can I get a refund?', '<p>In case the item you received is damaged or defective, you could return an item in the same condition as you received it with the original box and/or packaging intact. Once we receive the returned item, we will inspect it and if the item is found to be defective or damaged, we will process the refund along with any shipping fees incurred.<br></p>\r\n'),
(4, 'When are ‘Returns’ not possible?', '<p class=\"a  \" style=\"box-sizing: inherit; text-rendering: optimizeLegibility; line-height: 1.6; margin-bottom: 0.714286rem; padding: 0px; font-size: 14px; color: rgb(10, 10, 10); font-family: opensans, &quot;Helvetica Neue&quot;, Helvetica, Helvetica, Arial, sans-serif; background-color: rgb(250, 250, 250);\">There are a few certain scenarios where it is difficult for us to support returns:</p><ol style=\"box-sizing: inherit; line-height: 1.6; margin-right: 0px; margin-bottom: 0px; margin-left: 1.25rem; padding: 0px; list-style-position: outside; color: rgb(10, 10, 10); font-family: opensans, &quot;Helvetica Neue&quot;, Helvetica, Helvetica, Arial, sans-serif; font-size: 14px; background-color: rgb(250, 250, 250);\"><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Return request is made outside the specified time frame, of 15 days from delivery.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Product is used, damaged, or is not in the same condition as you received it.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Specific categories like innerwear, lingerie, socks and clothing freebies etc.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Defective products which are covered under the manufacturer\'s warranty.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Any consumable item which has been used or installed.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Products with tampered or missing serial numbers.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Anything missing from the package you\'ve received including price tags, labels, original packing, freebies and accessories.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Fragile items, hygiene related items.</li></ol>\r\n'),
(5, 'What are the items that cannot be returned?', '<p>The items that can not be returned are:</p><p>Clearance items clearly marked as such and displaying a No-Return Policy<br></p><p>When the offer notes states so specifically are items that cannot be returned.</p><p>Items that fall into the below product types-</p><ul><li>Underwear</li><li>Lingerie</li><li>Socks</li><li>Software</li><li>Music albums</li><li>Books</li><li>Swimwear</li><li>Beauty &amp; Fragrances</li><li>Hosiery</li></ul><p>Also, any consumable items that are used or installed cannot be returned. As outlined in consumer Protection Rights and concerning section on non-returnable items<br></p>'),
(1, 'How to find an item?', '<h3 class=\"checkout-complete-box font-bold txt16\" style=\"box-sizing: inherit; text-rendering: optimizeLegibility; margin: 0.2rem 0px 0.5rem; padding: 0px; line-height: 1.4; background-color: rgb(250, 250, 250);\"><font color=\"#222222\" face=\"opensans, Helvetica Neue, Helvetica, Helvetica, Arial, sans-serif\"><span style=\"font-size: 15.7143px;\">We have a wide range of fabulous products to choose from.</span></font></h3><h3 class=\"checkout-complete-box font-bold txt16\" style=\"box-sizing: inherit; text-rendering: optimizeLegibility; margin: 0.2rem 0px 0.5rem; padding: 0px; line-height: 1.4; background-color: rgb(250, 250, 250);\"><span style=\"font-size: 15.7143px; color: rgb(34, 34, 34); font-family: opensans, \"Helvetica Neue\", Helvetica, Helvetica, Arial, sans-serif;\">Tip 1: If you\'re looking for a specific product, use the keyword search box located at the top of the site. Simply type what you are looking for, and prepare to be amazed!</span></h3><h3 class=\"checkout-complete-box font-bold txt16\" style=\"box-sizing: inherit; text-rendering: optimizeLegibility; margin: 0.2rem 0px 0.5rem; padding: 0px; line-height: 1.4; background-color: rgb(250, 250, 250);\"><font color=\"#222222\" face=\"opensans, Helvetica Neue, Helvetica, Helvetica, Arial, sans-serif\"><span style=\"font-size: 15.7143px;\">Tip 2: If you want to explore a category of products, use the Shop Categories in the upper menu, and navigate through your favorite categories where we\'ll feature the best products in each.</span></font><br><br></h3>\r\n'),
(2, 'What is your return policy?', '<p><span style=\"color: rgb(10, 10, 10); font-family: opensans, &quot;Helvetica Neue&quot;, Helvetica, Helvetica, Arial, sans-serif; font-size: 14px; text-align: center;\">You have 15 days to make a refund request after your order has been delivered.</span><br></p>\r\n'),
(3, ' I received a defective/damaged item, can I get a refund?', '<p>In case the item you received is damaged or defective, you could return an item in the same condition as you received it with the original box and/or packaging intact. Once we receive the returned item, we will inspect it and if the item is found to be defective or damaged, we will process the refund along with any shipping fees incurred.<br></p>\r\n'),
(4, 'When are ‘Returns’ not possible?', '<p class=\"a  \" style=\"box-sizing: inherit; text-rendering: optimizeLegibility; line-height: 1.6; margin-bottom: 0.714286rem; padding: 0px; font-size: 14px; color: rgb(10, 10, 10); font-family: opensans, &quot;Helvetica Neue&quot;, Helvetica, Helvetica, Arial, sans-serif; background-color: rgb(250, 250, 250);\">There are a few certain scenarios where it is difficult for us to support returns:</p><ol style=\"box-sizing: inherit; line-height: 1.6; margin-right: 0px; margin-bottom: 0px; margin-left: 1.25rem; padding: 0px; list-style-position: outside; color: rgb(10, 10, 10); font-family: opensans, &quot;Helvetica Neue&quot;, Helvetica, Helvetica, Arial, sans-serif; font-size: 14px; background-color: rgb(250, 250, 250);\"><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Return request is made outside the specified time frame, of 15 days from delivery.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Product is used, damaged, or is not in the same condition as you received it.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Specific categories like innerwear, lingerie, socks and clothing freebies etc.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Defective products which are covered under the manufacturer\'s warranty.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Any consumable item which has been used or installed.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Products with tampered or missing serial numbers.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Anything missing from the package you\'ve received including price tags, labels, original packing, freebies and accessories.</li><li style=\"box-sizing: inherit; margin: 0px; padding: 0px; font-size: inherit;\">Fragile items, hygiene related items.</li></ol>\r\n'),
(5, 'What are the items that cannot be returned?', '<p>The items that can not be returned are:</p><p>Clearance items clearly marked as such and displaying a No-Return Policy<br></p><p>When the offer notes states so specifically are items that cannot be returned.</p><p>Items that fall into the below product types-</p><ul><li>Underwear</li><li>Lingerie</li><li>Socks</li><li>Software</li><li>Music albums</li><li>Books</li><li>Swimwear</li><li>Beauty &amp; Fragrances</li><li>Hosiery</li></ul><p>Also, any consumable items that are used or installed cannot be returned. As outlined in consumer Protection Rights and concerning section on non-returnable items<br></p>');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_features`
--

CREATE TABLE `tbl_features` (
  `id` int(11) NOT NULL,
  `icon` varchar(50) NOT NULL,
  `title` varchar(100) NOT NULL,
  `link` varchar(255) NOT NULL,
  `order_no` int(11) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_features`
--

INSERT INTO `tbl_features` (`id`, `icon`, `title`, `link`, `order_no`) VALUES
(1, 'fa-truck', 'Free Shipping', '#', 1),
(2, 'fa-shield', 'Secure Payment', '#', 2),
(3, 'fa-undo', 'Easy Returns', '#', 3),
(4, 'fa-headphones', '24/7 Support', '#', 4);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_food_orders`
--

CREATE TABLE `tbl_food_orders` (
  `order_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `restaurant_id` int(11) NOT NULL,
  `delivery_address` text NOT NULL,
  `delivery_latitude` decimal(10,8) NOT NULL,
  `delivery_longitude` decimal(11,8) NOT NULL,
  `order_total` decimal(10,2) NOT NULL,
  `delivery_fee` decimal(10,2) NOT NULL DEFAULT 0.00,
  `payment_method` varchar(50) NOT NULL,
  `payment_status` enum('pending','paid','refunded') NOT NULL DEFAULT 'pending',
  `order_status` enum('placed','accepted','preparing','out_for_delivery','delivered','cancelled') NOT NULL DEFAULT 'placed',
  `driver_id` int(11) DEFAULT NULL,
  `order_notes` text DEFAULT NULL,
  `placed_at` datetime DEFAULT current_timestamp(),
  `accepted_at` datetime DEFAULT NULL,
  `delivered_at` datetime DEFAULT NULL,
  `cancelled_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_food_order_items`
--

CREATE TABLE `tbl_food_order_items` (
  `order_item_id` int(11) NOT NULL,
  `order_id` int(11) NOT NULL,
  `item_id` int(11) NOT NULL,
  `quantity` int(11) NOT NULL,
  `price_at_order` decimal(10,2) NOT NULL,
  `notes` text DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_gov_projects`
--

CREATE TABLE `tbl_gov_projects` (
  `project_id` int(11) NOT NULL,
  `project_name` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `region` varchar(255) NOT NULL,
  `start_date` date DEFAULT NULL,
  `end_date` date DEFAULT NULL,
  `status` enum('planning','voting','approved','in_progress','completed','cancelled') NOT NULL DEFAULT 'planning',
  `total_votes_for` int(11) NOT NULL DEFAULT 0,
  `total_votes_against` int(11) NOT NULL DEFAULT 0,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_home_sections`
--

CREATE TABLE `tbl_home_sections` (
  `id` int(11) NOT NULL,
  `title` varchar(255) NOT NULL,
  `category_id` int(11) NOT NULL,
  `category_type` varchar(20) NOT NULL,
  `product_limit` int(11) DEFAULT 8,
  `order_no` int(11) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_home_tabs`
--

CREATE TABLE `tbl_home_tabs` (
  `id` int(11) NOT NULL,
  `tab_name` varchar(100) NOT NULL,
  `tab_icon` varchar(50) NOT NULL,
  `filter_type` enum('recommendation','free_shipping','top_sale','max_vouchered','overseas','premium','official') NOT NULL,
  `display_order` int(11) NOT NULL DEFAULT 0,
  `is_active` tinyint(1) NOT NULL DEFAULT 1
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_home_tabs`
--

INSERT INTO `tbl_home_tabs` (`id`, `tab_name`, `tab_icon`, `filter_type`, `display_order`, `is_active`) VALUES
(1, 'For You', 'fa-magic', 'recommendation', 1, 1),
(2, 'Free Shipping', 'fa-truck', 'free_shipping', 2, 1),
(3, 'Top Sale', 'fa-fire', 'top_sale', 3, 1),
(4, 'Official', 'fa-check-circle', 'official', 4, 1),
(5, 'Voucher King', 'fa-ticket', 'max_vouchered', 5, 1);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_hotels`
--

CREATE TABLE `tbl_hotels` (
  `hotel_id` int(11) NOT NULL,
  `owner_user_id` int(11) DEFAULT NULL,
  `travel_agency_id` int(11) DEFAULT NULL,
  `hotel_name` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `address` varchar(255) NOT NULL,
  `city` varchar(100) NOT NULL,
  `state` varchar(100) DEFAULT NULL,
  `zip` varchar(20) DEFAULT NULL,
  `phone` varchar(50) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `website` varchar(255) DEFAULT NULL,
  `latitude` decimal(10,8) NOT NULL,
  `longitude` decimal(11,8) NOT NULL,
  `star_rating` tinyint(1) DEFAULT NULL CHECK (`star_rating` >= 1 and `star_rating` <= 5),
  `check_in_time` time DEFAULT '14:00:00',
  `check_out_time` time DEFAULT '12:00:00',
  `main_photo` varchar(255) DEFAULT NULL,
  `other_photos` text DEFAULT NULL,
  `amenities_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`amenities_json`)),
  `status` enum('active','inactive','pending_approval','rejected') NOT NULL DEFAULT 'pending_approval',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_hotel_rooms`
--

CREATE TABLE `tbl_hotel_rooms` (
  `room_id` int(11) NOT NULL,
  `hotel_id` int(11) NOT NULL,
  `room_type_en` varchar(255) NOT NULL,
  `room_type_bn` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `base_price_per_night` decimal(10,2) NOT NULL,
  `max_occupancy` int(11) NOT NULL DEFAULT 1,
  `total_rooms_available` int(11) DEFAULT NULL,
  `current_available_rooms` int(11) DEFAULT NULL,
  `main_photo` varchar(255) DEFAULT NULL,
  `other_photos` text DEFAULT NULL,
  `amenities_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`amenities_json`)),
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_house_rentals`
--

CREATE TABLE `tbl_house_rentals` (
  `rental_id` int(11) NOT NULL,
  `owner_user_id` int(11) NOT NULL,
  `property_type` varchar(100) NOT NULL,
  `address` varchar(255) NOT NULL,
  `city` varchar(100) NOT NULL,
  `area` varchar(100) DEFAULT NULL,
  `latitude` decimal(10,8) NOT NULL,
  `longitude` decimal(11,8) NOT NULL,
  `rent_amount` decimal(10,2) NOT NULL,
  `deposit_amount` decimal(10,2) DEFAULT NULL,
  `bedrooms` int(11) DEFAULT NULL,
  `bathrooms` int(11) DEFAULT NULL,
  `square_feet` int(11) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `main_photo` varchar(255) DEFAULT NULL,
  `other_photos` text DEFAULT NULL,
  `availability_date` date DEFAULT NULL,
  `status` enum('available','rented','pending_approval','rejected') NOT NULL DEFAULT 'pending_approval',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_house_sales`
--

CREATE TABLE `tbl_house_sales` (
  `sale_id` int(11) NOT NULL,
  `owner_user_id` int(11) NOT NULL,
  `property_type` varchar(100) NOT NULL,
  `address` varchar(255) NOT NULL,
  `city` varchar(100) NOT NULL,
  `area` varchar(100) DEFAULT NULL,
  `latitude` decimal(10,8) NOT NULL,
  `longitude` decimal(11,8) NOT NULL,
  `selling_price` decimal(15,2) NOT NULL,
  `bedrooms` int(11) DEFAULT NULL,
  `bathrooms` int(11) DEFAULT NULL,
  `square_feet` int(11) DEFAULT NULL,
  `land_area_sqft` decimal(10,2) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `main_photo` varchar(255) DEFAULT NULL,
  `other_photos` text DEFAULT NULL,
  `status` enum('available','sold','pending_approval','rejected') NOT NULL DEFAULT 'pending_approval',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_kyc_verifications`
--

CREATE TABLE `tbl_kyc_verifications` (
  `kyc_id` int(11) NOT NULL,
  `cust_id` int(11) NOT NULL,
  `nid_number` varchar(50) NOT NULL,
  `nid_front` varchar(255) NOT NULL,
  `nid_back` varchar(255) DEFAULT NULL,
  `face_capture` varchar(255) NOT NULL,
  `gps_location` varchar(100) DEFAULT NULL,
  `status` enum('pending','approved','rejected') DEFAULT 'pending',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_kyc_verifications`
--

INSERT INTO `tbl_kyc_verifications` (`kyc_id`, `cust_id`, `nid_number`, `nid_front`, `nid_back`, `face_capture`, `gps_location`, `status`, `created_at`) VALUES
(1, 24, '', 'nid_front_24_1769328666.jpg', 'nid_back_24_1769327731.jpg', 'live_face_24_1769328696.jpg', NULL, 'pending', '2026-01-25 08:21:01');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_language`
--

CREATE TABLE `tbl_language` (
  `lang_id` int(11) NOT NULL,
  `lang_name` varchar(255) NOT NULL,
  `lang_value` text NOT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_language`
--

INSERT INTO `tbl_language` (`lang_id`, `lang_name`, `lang_value`) VALUES
(1, 'Currency', 'BDT  '),
(2, 'Search Product', 'Search Product'),
(3, 'Search', 'Search'),
(4, 'Submit', 'Submit'),
(5, 'Update', 'Update'),
(6, 'Read More', 'Read More'),
(7, 'Serial', 'Serial'),
(8, 'Photo', 'Photo'),
(9, 'Login', 'Login'),
(10, 'Customer Login', 'Customer Login'),
(11, 'Click here to login', 'Click here to login'),
(12, 'Back to Login Page', 'Back to Login Page'),
(13, 'Logged in as', 'Logged in as'),
(14, 'Logout', 'Logout'),
(15, 'Register', 'Register'),
(16, 'Customer Registration', 'Customer Registration'),
(17, 'Registration Successful', 'Registration Successful'),
(18, 'Cart', 'Cart'),
(19, 'View Cart', 'View Cart'),
(20, 'Update Cart', 'Update Cart'),
(21, 'Back to Cart', 'Back to Cart'),
(22, 'Checkout', 'Checkout'),
(23, 'Proceed to Checkout', 'Proceed to Checkout'),
(24, 'Orders', 'Orders'),
(25, 'Order History', 'Order History'),
(26, 'Order Details', 'Order Details'),
(27, 'Payment Date and Time', 'Payment Date and Time'),
(28, 'Transaction ID', 'Transaction ID'),
(29, 'Paid Amount', 'Paid Amount'),
(30, 'Payment Status', 'Payment Status'),
(31, 'Payment Method', 'Payment Method'),
(32, 'Payment ID', 'Payment ID'),
(33, 'Payment Section', 'Payment Section'),
(34, 'Select Payment Method', 'Select Payment Method'),
(35, 'Select a Method', 'Select a Method'),
(36, 'PayPal', 'PayPal'),
(37, 'Stripe', 'Stripe'),
(38, 'Bank Deposit', 'Bank Deposit'),
(39, 'Card Number', 'Card Number'),
(40, 'CVV', 'CVV'),
(41, 'Month', 'Month'),
(42, 'Year', 'Year'),
(43, 'Send to this Details', 'Send to this Details'),
(44, 'Transaction Information', 'Transaction Information'),
(45, 'Include transaction id and other information correctly', 'Include transaction id and other information correctly'),
(46, 'Pay Now', 'Pay Now'),
(47, 'Product Name', 'Product Name'),
(48, 'Product Details', 'Product Details'),
(49, 'Categories', 'Categories'),
(50, 'Category:', 'Category:'),
(51, 'All Products Under', 'All Products Under'),
(52, 'Select Size', 'Select Size'),
(53, 'Select Color', 'Select Color'),
(54, 'Product Price', 'Product Price'),
(55, 'Quantity', 'Quantity'),
(56, 'Out of Stock', 'Out of Stock'),
(57, 'Share This', 'Share This'),
(58, 'Share This Product', 'Share This Product'),
(59, 'Product Description', 'Product Description'),
(60, 'Features', 'Features'),
(61, 'Conditions', 'Conditions'),
(62, 'Return Policy', 'Return Policy'),
(63, 'Reviews', 'Reviews'),
(64, 'Review', 'Review'),
(65, 'Give a Review', 'Give a Review'),
(66, 'Write your comment (Optional)', 'Write your comment (Optional)'),
(67, 'Submit Review', 'Submit Review'),
(68, 'You already have given a rating!', 'You already have given a rating!'),
(69, 'You must have to login to give a review', 'You must have to login to give a review'),
(70, 'No description found', 'No description found'),
(71, 'No feature found', 'No feature found'),
(72, 'No condition found', 'No condition found'),
(73, 'No return policy found', 'No return policy found'),
(74, 'Review not found', 'Review not found'),
(75, 'Customer Name', 'Customer Name'),
(76, 'Comment', 'Comment'),
(77, 'Comments', 'Comments'),
(78, 'Rating', 'Rating'),
(79, 'Previous', 'Previous'),
(80, 'Next', 'Next'),
(81, 'Sub Total', 'Sub Total'),
(82, 'Total', 'Total'),
(83, 'Action', 'Action'),
(84, 'Shipping Cost', 'Shipping Cost'),
(85, 'Continue Shopping', 'Continue Shopping'),
(86, 'Update Billing Address', 'Update Billing Address'),
(87, 'Update Shipping Address', 'Update Shipping Address'),
(88, 'Update Billing and Shipping Info', 'Update Billing and Shipping Info'),
(89, 'Dashboard', 'Dashboard'),
(90, 'Welcome to the Dashboard', 'Welcome to the Dashboard'),
(91, 'Back to Dashboard', 'Back to Dashboard'),
(92, 'Subscribe', 'Subscribe'),
(93, 'Subscribe To Our Newsletter', 'Subscribe To Our Newsletter'),
(94, 'Email Address', 'Email Address'),
(95, 'Enter Your Email Address', 'Enter Your Email Address'),
(96, 'Password', 'Password'),
(97, 'Forget Password', 'Forget Password'),
(98, 'Retype Password', 'Retype Password'),
(99, 'Update Password', 'Update Password'),
(100, 'New Password', 'New Password'),
(101, 'Retype New Password', 'Retype New Password'),
(102, 'Full Name', 'Full Name'),
(103, 'Company Name', 'Company Name'),
(104, 'Phone Number', 'Phone Number'),
(105, 'Address', 'Address'),
(106, 'Country', 'Country'),
(107, 'City', 'City'),
(108, 'State', 'State'),
(109, 'Zip Code', 'Zip Code'),
(110, 'About Us', 'About Us'),
(111, 'Featured Posts', 'Featured Posts'),
(112, 'Popular Posts', 'Popular Posts'),
(113, 'Recent Posts', 'Recent Posts'),
(114, 'Contact Information', 'Contact Information'),
(115, 'Contact Form', 'Contact Form'),
(116, 'Our Office', 'Our Office'),
(117, 'Update Profile', 'Update Profile'),
(118, 'Send Message', 'Send Message'),
(119, 'Message', 'Message'),
(120, 'Find Us On Map', 'Find Us On Map'),
(121, 'Congratulation! Payment is successful.', 'Congratulation! Payment is successful.'),
(122, 'Billing and Shipping Information is updated successfully.', 'Billing and Shipping Information is updated successfully.'),
(123, 'Customer Name can not be empty.', 'Customer Name can not be empty.'),
(124, 'Phone Number can not be empty.', 'Phone Number can not be empty.'),
(125, 'Address can not be empty.', 'Address can not be empty.'),
(126, 'You must have to select a country.', 'You must have to select a country.'),
(127, 'City can not be empty.', 'City can not be empty.'),
(128, 'State can not be empty.', 'State can not be empty.'),
(129, 'Zip Code can not be empty.', 'Zip Code can not be empty.'),
(130, 'Profile Information is updated successfully.', 'Profile Information is updated successfully.'),
(131, 'Email Address can not be empty', 'Email Address can not be empty'),
(132, 'Email and/or Password can not be empty.', 'Email and/or Password can not be empty.'),
(133, 'Email Address does not match.', 'Email Address does not match.'),
(134, 'Email address must be valid.', 'Email address must be valid.'),
(135, 'You email address is not found in our system.', 'You email address is not found in our system.'),
(136, 'Please check your email and confirm your subscription.', 'Please check your email and confirm your subscription.'),
(137, 'Your email is verified successfully. You can now login to our website.', 'Your email is verified successfully. You can now login to our website.'),
(138, 'Password can not be empty.', 'Password can not be empty.'),
(139, 'Passwords do not match.', 'Passwords do not match.'),
(140, 'Please enter new and retype passwords.', 'Please enter new and retype passwords.'),
(141, 'Password is updated successfully.', 'Password is updated successfully.'),
(142, 'To reset your password, please click on the link below.', 'To reset your password, please click on the link below.'),
(143, 'PASSWORD RESET REQUEST - YOUR WEBSITE.COM', 'PASSWORD RESET REQUEST - YOUR WEBSITE.COM'),
(144, 'The password reset email time (24 hours) has expired. Please again try to reset your password.', 'The password reset email time (24 hours) has expired. Please again try to reset your password.'),
(145, 'A confirmation link is sent to your email address. You will get the password reset information in there.', 'A confirmation link is sent to your email address. You will get the password reset information in there.'),
(146, 'Password is reset successfully. You can now login.', 'Password is reset successfully. You can now login.'),
(147, 'Email Address Already Exists', 'Email Address Already Exists.'),
(148, 'Sorry! Your account is inactive. Please contact to the administrator.', 'Sorry! Your account is inactive. Please contact to the administrator.'),
(149, 'Change Password', 'Change Password'),
(150, 'Registration Email Confirmation for YOUR WEBSITE', 'Registration Email Confirmation for YOUR WEBSITE.'),
(151, 'Thank you for your registration! Your account has been created. To active your account click on the link below:', 'Thank you for your registration! Your account has been created. To active your account click on the link below:'),
(152, 'Your registration is completed. Please check your email address to follow the process to confirm your registration.', 'Your registration is completed. Please check your email address to follow the process to confirm your registration.'),
(153, 'No Product Found', 'No Product Found'),
(154, 'Add to Cart', 'Add to Cart'),
(155, 'Related Products', 'Related Products'),
(156, 'See all related products from below', 'See all the related products from below'),
(157, 'Size', 'Size'),
(158, 'Color', 'Color'),
(159, 'Price', 'Price'),
(160, 'Please login as customer to checkout', 'Please login as customer to checkout'),
(161, 'Billing Address', 'Billing Address'),
(162, 'Shipping Address', 'Shipping Address'),
(163, 'Rating is Submitted Successfully!', 'Rating is Submitted Successfully!'),
(1, 'Currency', 'BDT  '),
(2, 'Search Product', 'Search Product'),
(3, 'Search', 'Search'),
(4, 'Submit', 'Submit'),
(5, 'Update', 'Update'),
(6, 'Read More', 'Read More'),
(7, 'Serial', 'Serial'),
(8, 'Photo', 'Photo'),
(9, 'Login', 'Login'),
(10, 'Customer Login', 'Customer Login'),
(11, 'Click here to login', 'Click here to login'),
(12, 'Back to Login Page', 'Back to Login Page'),
(13, 'Logged in as', 'Logged in as'),
(14, 'Logout', 'Logout'),
(15, 'Register', 'Register'),
(16, 'Customer Registration', 'Customer Registration'),
(17, 'Registration Successful', 'Registration Successful'),
(18, 'Cart', 'Cart'),
(19, 'View Cart', 'View Cart'),
(20, 'Update Cart', 'Update Cart'),
(21, 'Back to Cart', 'Back to Cart'),
(22, 'Checkout', 'Checkout'),
(23, 'Proceed to Checkout', 'Proceed to Checkout'),
(24, 'Orders', 'Orders'),
(25, 'Order History', 'Order History'),
(26, 'Order Details', 'Order Details'),
(27, 'Payment Date and Time', 'Payment Date and Time'),
(28, 'Transaction ID', 'Transaction ID'),
(29, 'Paid Amount', 'Paid Amount'),
(30, 'Payment Status', 'Payment Status'),
(31, 'Payment Method', 'Payment Method'),
(32, 'Payment ID', 'Payment ID'),
(33, 'Payment Section', 'Payment Section'),
(34, 'Select Payment Method', 'Select Payment Method'),
(35, 'Select a Method', 'Select a Method'),
(36, 'PayPal', 'PayPal'),
(37, 'Stripe', 'Stripe'),
(38, 'Bank Deposit', 'Bank Deposit'),
(39, 'Card Number', 'Card Number'),
(40, 'CVV', 'CVV'),
(41, 'Month', 'Month'),
(42, 'Year', 'Year'),
(43, 'Send to this Details', 'Send to this Details'),
(44, 'Transaction Information', 'Transaction Information'),
(45, 'Include transaction id and other information correctly', 'Include transaction id and other information correctly'),
(46, 'Pay Now', 'Pay Now'),
(47, 'Product Name', 'Product Name'),
(48, 'Product Details', 'Product Details'),
(49, 'Categories', 'Categories'),
(50, 'Category:', 'Category:'),
(51, 'All Products Under', 'All Products Under'),
(52, 'Select Size', 'Select Size'),
(53, 'Select Color', 'Select Color'),
(54, 'Product Price', 'Product Price'),
(55, 'Quantity', 'Quantity'),
(56, 'Out of Stock', 'Out of Stock'),
(57, 'Share This', 'Share This'),
(58, 'Share This Product', 'Share This Product'),
(59, 'Product Description', 'Product Description'),
(60, 'Features', 'Features'),
(61, 'Conditions', 'Conditions'),
(62, 'Return Policy', 'Return Policy'),
(63, 'Reviews', 'Reviews'),
(64, 'Review', 'Review'),
(65, 'Give a Review', 'Give a Review'),
(66, 'Write your comment (Optional)', 'Write your comment (Optional)'),
(67, 'Submit Review', 'Submit Review'),
(68, 'You already have given a rating!', 'You already have given a rating!'),
(69, 'You must have to login to give a review', 'You must have to login to give a review'),
(70, 'No description found', 'No description found'),
(71, 'No feature found', 'No feature found'),
(72, 'No condition found', 'No condition found'),
(73, 'No return policy found', 'No return policy found'),
(74, 'Review not found', 'Review not found'),
(75, 'Customer Name', 'Customer Name'),
(76, 'Comment', 'Comment'),
(77, 'Comments', 'Comments'),
(78, 'Rating', 'Rating'),
(79, 'Previous', 'Previous'),
(80, 'Next', 'Next'),
(81, 'Sub Total', 'Sub Total'),
(82, 'Total', 'Total'),
(83, 'Action', 'Action'),
(84, 'Shipping Cost', 'Shipping Cost'),
(85, 'Continue Shopping', 'Continue Shopping'),
(86, 'Update Billing Address', 'Update Billing Address'),
(87, 'Update Shipping Address', 'Update Shipping Address'),
(88, 'Update Billing and Shipping Info', 'Update Billing and Shipping Info'),
(89, 'Dashboard', 'Dashboard'),
(90, 'Welcome to the Dashboard', 'Welcome to the Dashboard'),
(91, 'Back to Dashboard', 'Back to Dashboard'),
(92, 'Subscribe', 'Subscribe'),
(93, 'Subscribe To Our Newsletter', 'Subscribe To Our Newsletter'),
(94, 'Email Address', 'Email Address'),
(95, 'Enter Your Email Address', 'Enter Your Email Address'),
(96, 'Password', 'Password'),
(97, 'Forget Password', 'Forget Password'),
(98, 'Retype Password', 'Retype Password'),
(99, 'Update Password', 'Update Password'),
(100, 'New Password', 'New Password'),
(101, 'Retype New Password', 'Retype New Password'),
(102, 'Full Name', 'Full Name'),
(103, 'Company Name', 'Company Name'),
(104, 'Phone Number', 'Phone Number'),
(105, 'Address', 'Address'),
(106, 'Country', 'Country'),
(107, 'City', 'City'),
(108, 'State', 'State'),
(109, 'Zip Code', 'Zip Code'),
(110, 'About Us', 'About Us'),
(111, 'Featured Posts', 'Featured Posts'),
(112, 'Popular Posts', 'Popular Posts'),
(113, 'Recent Posts', 'Recent Posts'),
(114, 'Contact Information', 'Contact Information'),
(115, 'Contact Form', 'Contact Form'),
(116, 'Our Office', 'Our Office'),
(117, 'Update Profile', 'Update Profile'),
(118, 'Send Message', 'Send Message'),
(119, 'Message', 'Message'),
(120, 'Find Us On Map', 'Find Us On Map'),
(121, 'Congratulation! Payment is successful.', 'Congratulation! Payment is successful.'),
(122, 'Billing and Shipping Information is updated successfully.', 'Billing and Shipping Information is updated successfully.'),
(123, 'Customer Name can not be empty.', 'Customer Name can not be empty.'),
(124, 'Phone Number can not be empty.', 'Phone Number can not be empty.'),
(125, 'Address can not be empty.', 'Address can not be empty.'),
(126, 'You must have to select a country.', 'You must have to select a country.'),
(127, 'City can not be empty.', 'City can not be empty.'),
(128, 'State can not be empty.', 'State can not be empty.'),
(129, 'Zip Code can not be empty.', 'Zip Code can not be empty.'),
(130, 'Profile Information is updated successfully.', 'Profile Information is updated successfully.'),
(131, 'Email Address can not be empty', 'Email Address can not be empty'),
(132, 'Email and/or Password can not be empty.', 'Email and/or Password can not be empty.'),
(133, 'Email Address does not match.', 'Email Address does not match.'),
(134, 'Email address must be valid.', 'Email address must be valid.'),
(135, 'You email address is not found in our system.', 'You email address is not found in our system.'),
(136, 'Please check your email and confirm your subscription.', 'Please check your email and confirm your subscription.'),
(137, 'Your email is verified successfully. You can now login to our website.', 'Your email is verified successfully. You can now login to our website.'),
(138, 'Password can not be empty.', 'Password can not be empty.'),
(139, 'Passwords do not match.', 'Passwords do not match.'),
(140, 'Please enter new and retype passwords.', 'Please enter new and retype passwords.'),
(141, 'Password is updated successfully.', 'Password is updated successfully.'),
(142, 'To reset your password, please click on the link below.', 'To reset your password, please click on the link below.'),
(143, 'PASSWORD RESET REQUEST - YOUR WEBSITE.COM', 'PASSWORD RESET REQUEST - YOUR WEBSITE.COM'),
(144, 'The password reset email time (24 hours) has expired. Please again try to reset your password.', 'The password reset email time (24 hours) has expired. Please again try to reset your password.'),
(145, 'A confirmation link is sent to your email address. You will get the password reset information in there.', 'A confirmation link is sent to your email address. You will get the password reset information in there.'),
(146, 'Password is reset successfully. You can now login.', 'Password is reset successfully. You can now login.'),
(147, 'Email Address Already Exists', 'Email Address Already Exists.'),
(148, 'Sorry! Your account is inactive. Please contact to the administrator.', 'Sorry! Your account is inactive. Please contact to the administrator.'),
(149, 'Change Password', 'Change Password'),
(150, 'Registration Email Confirmation for YOUR WEBSITE', 'Registration Email Confirmation for YOUR WEBSITE.'),
(151, 'Thank you for your registration! Your account has been created. To active your account click on the link below:', 'Thank you for your registration! Your account has been created. To active your account click on the link below:'),
(152, 'Your registration is completed. Please check your email address to follow the process to confirm your registration.', 'Your registration is completed. Please check your email address to follow the process to confirm your registration.'),
(153, 'No Product Found', 'No Product Found'),
(154, 'Add to Cart', 'Add to Cart'),
(155, 'Related Products', 'Related Products'),
(156, 'See all related products from below', 'See all the related products from below'),
(157, 'Size', 'Size'),
(158, 'Color', 'Color'),
(159, 'Price', 'Price'),
(160, 'Please login as customer to checkout', 'Please login as customer to checkout'),
(161, 'Billing Address', 'Billing Address'),
(162, 'Shipping Address', 'Shipping Address'),
(163, 'Rating is Submitted Successfully!', 'Rating is Submitted Successfully!');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_menu_categories`
--

CREATE TABLE `tbl_menu_categories` (
  `menu_category_id` int(11) NOT NULL,
  `restaurant_id` int(11) NOT NULL,
  `category_name_en` varchar(255) NOT NULL,
  `category_name_bn` varchar(255) DEFAULT NULL,
  `display_order` int(11) NOT NULL DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_menu_items`
--

CREATE TABLE `tbl_menu_items` (
  `item_id` int(11) NOT NULL,
  `restaurant_id` int(11) NOT NULL,
  `menu_category_id` int(11) DEFAULT NULL,
  `item_name_en` varchar(255) NOT NULL,
  `item_name_bn` varchar(255) DEFAULT NULL,
  `description_en` text DEFAULT NULL,
  `description_bn` text DEFAULT NULL,
  `price` decimal(10,2) NOT NULL,
  `is_available` tinyint(1) NOT NULL DEFAULT 1,
  `photo` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_mid_category`
--

CREATE TABLE `tbl_mid_category` (
  `mcat_id` int(11) NOT NULL,
  `mcat_name` varchar(255) NOT NULL,
  `tcat_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_mid_category`
--

INSERT INTO `tbl_mid_category` (`mcat_id`, `mcat_name`, `tcat_id`) VALUES
(1, 'Men Accessories', 1),
(2, 'Men\'s Shoes', 1),
(3, 'Beauty Products', 2),
(4, 'Accessories', 2),
(6, 'Shoes', 2),
(7, 'Clothing', 2),
(8, 'Bottoms', 1),
(9, 'T-shirts & Shirts', 1),
(10, 'Clothing', 3),
(11, 'Shoes', 3),
(12, 'Accessories', 3),
(14, 'Electronic Items', 4),
(15, 'Computers', 4),
(16, 'Health', 5),
(17, 'Household', 5),
(0, 'r', 1),
(1, 'Men Accessories', 1),
(2, 'Men\'s Shoes', 1),
(3, 'Beauty Products', 2),
(4, 'Accessories', 2),
(6, 'Shoes', 2),
(7, 'Clothing', 2),
(8, 'Bottoms', 1),
(9, 'T-shirts & Shirts', 1),
(10, 'Clothing', 3),
(11, 'Shoes', 3),
(12, 'Accessories', 3),
(14, 'Electronic Items', 4),
(15, 'Computers', 4),
(16, 'Health', 5),
(17, 'Household', 5),
(0, 'r', 1);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_order`
--

CREATE TABLE `tbl_order` (
  `id` int(11) NOT NULL,
  `cust_id` int(11) DEFAULT NULL,
  `product_id` int(11) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `size` varchar(100) NOT NULL,
  `color` varchar(100) NOT NULL,
  `quantity` varchar(50) NOT NULL,
  `unit_price` varchar(50) NOT NULL,
  `payment_id` varchar(255) NOT NULL,
  `coupon_code` varchar(100) DEFAULT NULL,
  `coupon_discount` decimal(10,2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_order`
--

INSERT INTO `tbl_order` (`id`, `cust_id`, `product_id`, `product_name`, `size`, `color`, `quantity`, `unit_price`, `payment_id`, `coupon_code`, `coupon_discount`) VALUES
(1, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', '1749215216', NULL, NULL),
(2, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', '1749241730', NULL, NULL),
(3, 0, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '1', '169', '1749241730', NULL, NULL),
(4, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'COD-1749286625', NULL, NULL),
(5, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'COD-1749287800', NULL, NULL),
(6, 0, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '1', '169', 'COD-1749287800', NULL, NULL),
(7, 0, 85, 'Men\'s Soft Classic Sneaker', '38', 'Dark Clay', '1', '91', 'COD-1749292105', NULL, NULL),
(8, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'COD-1749292416', NULL, NULL),
(9, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'COD-1749301633', NULL, NULL),
(10, 0, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '1', '169', 'SSL_68443ede5636a', NULL, NULL),
(11, 0, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '1', '169', 'SSL_6844451b41668', NULL, NULL),
(12, 0, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '1', '169', 'SSL_6844456e0663f', NULL, NULL),
(13, 0, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '1', '169', 'COD-1749305069', NULL, NULL),
(14, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'SSL_68444786546cc', NULL, NULL),
(15, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'SSL_684449677d3c8', NULL, NULL),
(16, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '6', '179', 'SSL_68444b5e1de0f', NULL, NULL),
(17, 0, 97, 'Women\'s Tea Length Dress with Rosette Detail (Petite & Regular)', '31', 'Navy', '1', '67', 'SSL_68444c281e76a', NULL, NULL),
(18, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_68444d176ca51', NULL, NULL),
(19, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_68444f1d057d7', NULL, NULL),
(20, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_68444fbb54448', NULL, NULL),
(21, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_684451962084a', NULL, NULL),
(22, 0, 98, 'Women\'s Fuzzy Fleece Lapel Open Front Long Cardigan Coat', 'L', 'Green', '1', '43', 'SSL_68445a2067b6c', NULL, NULL),
(23, 0, 98, 'Women\'s Fuzzy Fleece Lapel Open Front Long Cardigan Coat', 'L', 'Green', '1', '43', 'SSL_6844661518150', NULL, NULL),
(24, 0, 98, 'Women\'s Fuzzy Fleece Lapel Open Front Long Cardigan Coat', 'L', 'Green', '1', '43', 'COD-1749313528', NULL, NULL),
(25, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'COD-1749330843', NULL, NULL),
(26, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'COD-1749330905', NULL, NULL),
(27, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'SSL_684597f7bf0f5', NULL, NULL),
(28, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'SSL_68459837d961e', NULL, NULL),
(29, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'SSL_68459bcebd19d', NULL, NULL),
(30, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'COD-1749392363', NULL, NULL),
(31, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'COD-1749393685', NULL, NULL),
(32, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'COD-1749394029', NULL, NULL),
(33, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'COD-1749394175', NULL, NULL),
(34, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'COD-1749394367', NULL, NULL),
(35, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'COD-1749399833-0', NULL, NULL),
(36, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'COD-1749399916-0', NULL, NULL),
(37, 0, 97, 'Women\'s Tea Length Dress with Rosette Detail (Petite & Regular)', '31', 'Navy', '1', '67', 'SSL_6845c65f37b96', NULL, NULL),
(38, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'SSL_6845c65f37b96', NULL, NULL),
(39, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_6845c78c379f1', NULL, NULL),
(40, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'COD-1749409655-0', NULL, NULL),
(41, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_6845ea105cde0', NULL, NULL),
(42, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_6845ea94cadae', NULL, NULL),
(43, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_6845f64b56201', NULL, NULL),
(44, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'SSL_6845fad1a64de', NULL, NULL),
(45, 0, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'SSL_684606d21f64c', NULL, NULL),
(46, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'SSL_684607e87a203', NULL, NULL),
(47, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'SSL_68467102cb6a7', NULL, NULL),
(48, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_6846909e64339', NULL, NULL),
(49, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'SSL_684708b59cecd', NULL, NULL),
(50, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_6848d3b9a569b', NULL, NULL),
(51, 0, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '1', '169', 'SSL_6848d96da530d', NULL, NULL),
(52, 0, 97, 'Women\'s Tea Length Dress with Rosette Detail (Petite & Regular)', '31', 'Navy', '1', '67', 'SSL_6848d9f9754a7', NULL, NULL),
(53, 0, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'SSL_6848daa7dca39', NULL, NULL),
(54, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_6848db0032183', NULL, NULL),
(55, 0, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_6848dc389c1ae', NULL, NULL),
(56, 0, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '1', '169', 'SSL_6848ecdfa2def', NULL, NULL),
(57, 0, 83, 'Men\'s Ultra Cotton T-Shirt, Multipack', 'XS', 'Red', '1', '19', 'COD-1749609836-0', NULL, NULL),
(78, 21, 90, 'Women\'s Thin Cotton Zip Up Hoodie Jacket', 'XS', 'Black', '1', '32', 'SSL_6918396315102', NULL, NULL),
(83, 21, 94, 'WD 5TB Elements Portable External Hard Drive HDD', '5T', 'Black', '1', '149', 'SSL_69184a7d73ad4', NULL, NULL),
(84, 21, 98, 'Women\'s Fuzzy Fleece Lapel Open Front Long Cardigan Coat', 'L', 'Green', '1', '43', 'SSL_69184ae0aaa50', NULL, NULL),
(85, 21, 94, 'WD 5TB Elements Portable External Hard Drive HDD', '5T', 'Black', '1', '149', 'SSL_69184ae0aaa50', NULL, NULL),
(86, 21, 94, 'WD 5TB Elements Portable External Hard Drive HDD', '5T', 'Black', '1', '149', 'SSL_69184bc3574ea', NULL, NULL),
(87, 21, 86, 'Amazfit GTS 3 Smart Watch for Android iPhone', 'Free Size', 'Black', '1', '179', 'SSL_69184c769fc50', NULL, NULL),
(88, 21, 93, 'Gold Plated Leopard Print Crystal Big Round Hoop Earrings', 'One Size for All', 'Gold', '1', '25', 'SSL_69184c98314da', NULL, NULL),
(89, 21, 83, 'Men\'s Ultra Cotton T-Shirt, Multipack', 'XS', 'Red', '1', '19', 'SSL_69184d1e5a34d', NULL, NULL),
(90, 21, 84, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', 'S', 'Black', '1', '39', 'SSL_691850e745eeb', NULL, NULL),
(92, 21, 97, 'Women\'s Tea Length Dress with Rosette Detail (Petite & Regular)', '31', 'Navy', '1', '67', 'SSL_691851e733f52', NULL, NULL),
(93, 21, 97, 'Women\'s Tea Length Dress with Rosette Detail (Petite & Regular)', '31', 'Navy', '1', '67', 'SSL_6918523f4f85c', NULL, NULL),
(94, 21, 98, 'Women\'s Fuzzy Fleece Lapel Open Front Long Cardigan Coat', 'L', 'Green', '1', '43', 'SSL_6918540650c1d', NULL, NULL),
(95, 21, 98, 'Women\'s Fuzzy Fleece Lapel Open Front Long Cardigan Coat', 'L', 'Green', '1', '43', 'SSL_6918540e617f1', NULL, NULL),
(96, 21, 98, 'Women\'s Fuzzy Fleece Lapel Open Front Long Cardigan Coat', 'L', 'Green', '1', '43', 'SSL_6918541dc7c00', NULL, NULL),
(97, 21, 98, 'Women\'s Fuzzy Fleece Lapel Open Front Long Cardigan Coat', 'L', 'Green', '1', '43', 'SSL_691854514b8c6', NULL, NULL),
(98, 21, 91, 'Women\'s Oversized Fleece Hoodie', 'S', 'Olive', '1', '56', 'SSL_6918562e7806e', NULL, NULL),
(99, 21, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '1', '169', 'SSL_691896efbcc46', NULL, NULL),
(101, 21, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '1', '279', 'SSL_691897e3982bd', NULL, NULL),
(104, 23, 95, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', 'One Size for All', 'Black', '24', '279', 'SSL_695d4ff268c81', NULL, NULL),
(106, 23, 99, 'Oculus Quest 2 - Advanced All-In-One Virtual Reality Headset', '256 GB', 'White', '1', '495.00', 'SSL_6961518ea8a29', NULL, NULL),
(107, 23, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37', 'SSL_696153d65e40b', NULL, NULL),
(110, 23, 94, 'WD 5TB Elements Portable External Hard Drive HDD', '5T', 'Black', '1', '149', 'SSL_696def80526b9', NULL, NULL),
(111, 23, 94, 'WD 5TB Elements Portable External Hard Drive HDD', '5T', 'Black', '1', '149.00', 'SSL_696df011cfab3', NULL, NULL),
(112, 23, 83, 'Men\'s Ultra Cotton T-Shirt, Multipack', 'XS', 'Red', '1', '19', 'SSL_696df011cfab3', NULL, NULL),
(113, 23, 93, 'Gold Plated Leopard Print Crystal Big Round Hoop Earrings', 'One Size for All', 'Gold', '2', '25', 'COD-1768813009-233488', '', 0.00),
(114, 23, 83, 'Men\'s Ultra Cotton T-Shirt, Multipack', 'XS', 'Red', '1', '19', 'SSL_696df4119e8d2', NULL, NULL),
(115, 23, 90, 'Women\'s Thin Cotton Zip Up Hoodie Jacket', 'XS', 'Black', '1', '32', 'SSL_696fdbd7ac2c6', NULL, NULL),
(116, 24, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37.00', 'SSL_6970d26ee15bf', NULL, NULL),
(117, 24, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '2', '169', 'SSL_6970d26ee15bf', NULL, NULL),
(118, 24, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37.00', 'SSL_6971dcc461c1e', NULL, NULL),
(119, 24, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '2', '169.00', 'SSL_6971dcc461c1e', NULL, NULL),
(120, 24, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37.00', 'SSL_6971dd5de1748', NULL, NULL),
(121, 24, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '2', '169.00', 'SSL_6971dd5de1748', NULL, NULL),
(122, 24, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37.00', 'SSL_6971dd6e9e31a', NULL, NULL),
(123, 24, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '2', '169.00', 'SSL_6971dd6e9e31a', NULL, NULL),
(124, 24, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37.00', 'SSL_6971ddc16df98', NULL, NULL),
(125, 24, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '2', '169.00', 'SSL_6971ddc16df98', NULL, NULL),
(126, 24, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37.00', 'SSL_6971de675c938', NULL, NULL),
(127, 24, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '2', '169.00', 'SSL_6971de675c938', NULL, NULL),
(128, 24, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37.00', 'SSL_6971dea8351d0', NULL, NULL),
(129, 24, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '2', '169.00', 'SSL_6971dea8351d0', NULL, NULL),
(130, 24, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37.00', 'SSL_6971e0520728e', NULL, NULL),
(131, 24, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '2', '169.00', 'SSL_6971e0520728e', NULL, NULL),
(132, 24, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37.00', 'SSL_6971e1b30f688', NULL, NULL),
(133, 24, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '2', '169.00', 'SSL_6971e1b30f688', NULL, NULL),
(134, 24, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37.00', 'SSL_6971e1fc8d952', NULL, NULL),
(135, 24, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '2', '169.00', 'SSL_6971e1fc8d952', NULL, NULL),
(136, 24, 87, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '12 Months', 'Blue', '1', '37.00', 'SSL_6971e7ebca8c4', NULL, NULL),
(137, 24, 102, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '14 Plus', 'Black', '2', '169.00', 'SSL_6971e7ebca8c4', NULL, NULL);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_page`
--

CREATE TABLE `tbl_page` (
  `id` int(11) NOT NULL,
  `about_title` varchar(255) NOT NULL,
  `about_content` text NOT NULL,
  `about_banner` varchar(255) NOT NULL,
  `about_meta_title` varchar(255) NOT NULL,
  `about_meta_keyword` text NOT NULL,
  `about_meta_description` text NOT NULL,
  `faq_title` varchar(255) NOT NULL,
  `faq_banner` varchar(255) NOT NULL,
  `faq_meta_title` varchar(255) NOT NULL,
  `faq_meta_keyword` text NOT NULL,
  `faq_meta_description` text NOT NULL,
  `blog_title` varchar(255) NOT NULL,
  `blog_banner` varchar(255) NOT NULL,
  `blog_meta_title` varchar(255) NOT NULL,
  `blog_meta_keyword` text NOT NULL,
  `blog_meta_description` text NOT NULL,
  `contact_title` varchar(255) NOT NULL,
  `contact_banner` varchar(255) NOT NULL,
  `contact_meta_title` varchar(255) NOT NULL,
  `contact_meta_keyword` text NOT NULL,
  `contact_meta_description` text NOT NULL,
  `pgallery_title` varchar(255) NOT NULL,
  `pgallery_banner` varchar(255) NOT NULL,
  `pgallery_meta_title` varchar(255) NOT NULL,
  `pgallery_meta_keyword` text NOT NULL,
  `pgallery_meta_description` text NOT NULL,
  `vgallery_title` varchar(255) NOT NULL,
  `vgallery_banner` varchar(255) NOT NULL,
  `vgallery_meta_title` varchar(255) NOT NULL,
  `vgallery_meta_keyword` text NOT NULL,
  `vgallery_meta_description` text NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_page`
--

INSERT INTO `tbl_page` (`id`, `about_title`, `about_content`, `about_banner`, `about_meta_title`, `about_meta_keyword`, `about_meta_description`, `faq_title`, `faq_banner`, `faq_meta_title`, `faq_meta_keyword`, `faq_meta_description`, `blog_title`, `blog_banner`, `blog_meta_title`, `blog_meta_keyword`, `blog_meta_description`, `contact_title`, `contact_banner`, `contact_meta_title`, `contact_meta_keyword`, `contact_meta_description`, `pgallery_title`, `pgallery_banner`, `pgallery_meta_title`, `pgallery_meta_keyword`, `pgallery_meta_description`, `vgallery_title`, `vgallery_banner`, `vgallery_meta_title`, `vgallery_meta_keyword`, `vgallery_meta_description`) VALUES
(1, 'About Us', '<p style=\"border: 0px solid; margin-top: 1.5rem; margin-bottom: 0px;\">Welcome to Ecommerce PHP Project!</p><p style=\"border: 0px solid; margin-top: 1.5rem; margin-bottom: 0px;\"><span style=\"border: 0px solid;\">We aim to offer our customers a variety of the latest [PRODUCTS_CATEGORY_NAME]. Weâ€™ve come a long way, so we know exactly which direction to take when supplying you with high quality yet budget-friendly products. We offer all of this while providing excellent customer service and friendly support.</span></p><p style=\"border: 0px solid; margin-top: 1.5rem; margin-bottom: 0px;\"><span style=\"border: 0px solid;\">We always keep an eye on the latest trends in [PRODUCTS CATEGORY NAME] and put our customersâ€™ wishes first. That is why we have satisfied customers all over the world, and are thrilled to be a part of the [PRODUCTS CATEGORY NAME] industry.</span></p><p style=\"border: 0px solid; margin-top: 1.5rem; margin-bottom: 0px;\"><span style=\"border: 0px solid;\">The interests of our customers are always top priority for us, so we hope you will enjoy our products as much as we enjoy making them available to you.</span></p><p style=\"\">We make sure you get the best quality outfits with hassle free returns and exchanges policy. We ensure what you see is exactly what you get!</p><ul><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Low Price Guarantee</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">24/7 Customer Support</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">E-Mail - Text - Call</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">We are here for you 24/7 online and via phone.</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Sizing & Color</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Worldwide Shipping</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Weâ€™d love to expand our business Internationally soon.</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Easy Returns</span></font></li></ul><p style=\"text-align: justify; \"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Bought an outfit but want to return it? We have a 3 days easy return policy. Please mail us at support@ecommercephp.com for more details.</span></font></p><p style=\"text-align: justify; \"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\"><b>Dream Dresses for Every Occasion</b></span></font></p><p style=\"text-align: justify; \"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Fashionys.com carries all carefully handpicked by our stylists. If youâ€™re interested in a particular model please mail us we will try our best to offer you the loved dress.</span></font></p><p style=\"text-align: justify; \"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\"><b>Verified Security</b></span></font></p><p style=\"text-align: justify; \"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">All our transactions are Verified by Norton and with the highest standards of security. Plus, there\'s a lot to go around too through regular exciting offers and gifts, so spread the word and refer us to everyone from your family, friends and colleagues and get rewarded for it. And to top it all, you can share your user experience by posting reviews. Donâ€™t wait any longer Sign up with us now! start stalking, start buying and start loving and start Introducing the beauty in you.</span></font></p>\r\n', 'about-banner.jpg', 'Ecommerce PHP - About Us', 'about, about us, about fashion, about company, about ecommerce php project', 'Our goal has always been to get the best in you we brought a huge collection whether youâ€™re attending a party, wedding, and all those events that require a WOW dress.', 'FAQ', 'faq-banner.jpg', 'Fashionys.com - FAQ', '', '', 'Blog', 'blog-banner.jpg', 'Ecommerce - Blog', '', '', 'Contact Us', 'contact-banner.jpg', 'Fashionys.com - Contact', '', '', 'Photo Gallery', 'pgallery-banner.jpg', 'Ecommerce - Photo Gallery', '', '', 'Video Gallery', 'vgallery-banner.jpg', 'Ecommerce - Video Gallery', '', ''),
(1, 'About Us', '<p style=\"border: 0px solid; margin-top: 1.5rem; margin-bottom: 0px;\">Welcome to Ecommerce PHP Project!</p><p style=\"border: 0px solid; margin-top: 1.5rem; margin-bottom: 0px;\"><span style=\"border: 0px solid;\">We aim to offer our customers a variety of the latest [PRODUCTS_CATEGORY_NAME]. Weâ€™ve come a long way, so we know exactly which direction to take when supplying you with high quality yet budget-friendly products. We offer all of this while providing excellent customer service and friendly support.</span></p><p style=\"border: 0px solid; margin-top: 1.5rem; margin-bottom: 0px;\"><span style=\"border: 0px solid;\">We always keep an eye on the latest trends in [PRODUCTS CATEGORY NAME] and put our customersâ€™ wishes first. That is why we have satisfied customers all over the world, and are thrilled to be a part of the [PRODUCTS CATEGORY NAME] industry.</span></p><p style=\"border: 0px solid; margin-top: 1.5rem; margin-bottom: 0px;\"><span style=\"border: 0px solid;\">The interests of our customers are always top priority for us, so we hope you will enjoy our products as much as we enjoy making them available to you.</span></p><p style=\"\">We make sure you get the best quality outfits with hassle free returns and exchanges policy. We ensure what you see is exactly what you get!</p><ul><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Low Price Guarantee</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">24/7 Customer Support</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">E-Mail - Text - Call</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">We are here for you 24/7 online and via phone.</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Sizing & Color</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Worldwide Shipping</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Weâ€™d love to expand our business Internationally soon.</span></font></li><li style=\"text-align: justify;\"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Easy Returns</span></font></li></ul><p style=\"text-align: justify; \"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Bought an outfit but want to return it? We have a 3 days easy return policy. Please mail us at support@ecommercephp.com for more details.</span></font></p><p style=\"text-align: justify; \"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\"><b>Dream Dresses for Every Occasion</b></span></font></p><p style=\"text-align: justify; \"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">Fashionys.com carries all carefully handpicked by our stylists. If youâ€™re interested in a particular model please mail us we will try our best to offer you the loved dress.</span></font></p><p style=\"text-align: justify; \"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\"><b>Verified Security</b></span></font></p><p style=\"text-align: justify; \"><font face=\"apercu, Arial, sans-serif\"><span style=\"font-size: 14px;\">All our transactions are Verified by Norton and with the highest standards of security. Plus, there\'s a lot to go around too through regular exciting offers and gifts, so spread the word and refer us to everyone from your family, friends and colleagues and get rewarded for it. And to top it all, you can share your user experience by posting reviews. Donâ€™t wait any longer Sign up with us now! start stalking, start buying and start loving and start Introducing the beauty in you.</span></font></p>\r\n', 'about-banner.jpg', 'Ecommerce PHP - About Us', 'about, about us, about fashion, about company, about ecommerce php project', 'Our goal has always been to get the best in you we brought a huge collection whether youâ€™re attending a party, wedding, and all those events that require a WOW dress.', 'FAQ', 'faq-banner.jpg', 'Fashionys.com - FAQ', '', '', 'Blog', 'blog-banner.jpg', 'Ecommerce - Blog', '', '', 'Contact Us', 'contact-banner.jpg', 'Fashionys.com - Contact', '', '', 'Photo Gallery', 'pgallery-banner.jpg', 'Ecommerce - Photo Gallery', '', '', 'Video Gallery', 'vgallery-banner.jpg', 'Ecommerce - Video Gallery', '', '');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_payment`
--

CREATE TABLE `tbl_payment` (
  `id` int(11) NOT NULL,
  `customer_id` int(11) NOT NULL,
  `customer_name` varchar(255) NOT NULL,
  `customer_email` varchar(255) NOT NULL,
  `payment_date` varchar(50) NOT NULL,
  `txnid` varchar(255) NOT NULL,
  `paid_amount` int(11) NOT NULL,
  `card_number` varchar(50) DEFAULT NULL,
  `card_cvv` varchar(10) DEFAULT NULL,
  `card_month` varchar(10) DEFAULT NULL,
  `card_year` varchar(10) DEFAULT NULL,
  `bank_transaction_info` text DEFAULT NULL,
  `payment_method` varchar(20) NOT NULL,
  `payment_status` varchar(25) NOT NULL,
  `shipping_status` varchar(20) NOT NULL,
  `payment_id` varchar(255) NOT NULL,
  `payment_note` text DEFAULT NULL,
  `ssl_payment_method` varchar(100) DEFAULT NULL,
  `billing_name` varchar(255) DEFAULT NULL,
  `billing_cname` varchar(255) DEFAULT NULL,
  `billing_phone` varchar(50) DEFAULT NULL,
  `billing_country` varchar(255) DEFAULT NULL,
  `billing_address` text DEFAULT NULL,
  `billing_city` varchar(100) DEFAULT NULL,
  `billing_state` varchar(100) DEFAULT NULL,
  `billing_zip` varchar(20) DEFAULT NULL,
  `shipping_name` varchar(255) DEFAULT NULL,
  `shipping_cname` varchar(255) DEFAULT NULL,
  `shipping_phone` varchar(50) DEFAULT NULL,
  `shipping_country` varchar(255) DEFAULT NULL,
  `shipping_address` text DEFAULT NULL,
  `shipping_city` varchar(100) DEFAULT NULL,
  `shipping_state` varchar(100) DEFAULT NULL,
  `shipping_zip` varchar(20) DEFAULT NULL,
  `shipping_cost` decimal(10,2) DEFAULT NULL,
  `coupon_code` varchar(100) DEFAULT NULL,
  `coupon_discount` decimal(10,2) DEFAULT 0.00,
  `coupon_id` int(11) DEFAULT NULL,
  `billing_email` varchar(100) DEFAULT NULL,
  `billing_street` varchar(255) DEFAULT NULL,
  `shipping_street` varchar(255) DEFAULT NULL,
  `shipping_email` varchar(100) DEFAULT NULL,
  `customer_note` text DEFAULT NULL,
  `card_holder_name` varchar(255) DEFAULT NULL,
  `card_security_code` varchar(255) DEFAULT NULL,
  `card_expiry_month` varchar(255) DEFAULT NULL,
  `card_expiry_year` varchar(255) DEFAULT NULL
) ENGINE=MyISAM DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_payment`
--

INSERT INTO `tbl_payment` (`id`, `customer_id`, `customer_name`, `customer_email`, `payment_date`, `txnid`, `paid_amount`, `card_number`, `card_cvv`, `card_month`, `card_year`, `bank_transaction_info`, `payment_method`, `payment_status`, `shipping_status`, `payment_id`, `payment_note`, `ssl_payment_method`, `billing_name`, `billing_cname`, `billing_phone`, `billing_country`, `billing_address`, `billing_city`, `billing_state`, `billing_zip`, `shipping_name`, `shipping_cname`, `shipping_phone`, `shipping_country`, `shipping_address`, `shipping_city`, `shipping_state`, `shipping_zip`, `shipping_cost`, `coupon_code`, `coupon_discount`, `coupon_id`, `billing_email`, `billing_street`, `shipping_street`, `shipping_email`, `customer_note`, `card_holder_name`, `card_security_code`, `card_expiry_month`, `card_expiry_year`) VALUES
(1, 0, 'Joy Saha', 's@1.com', '2025-06-10 19:41:35', 'SSL_6848ecdfa2def', 269, '', '', '', '', '', 'SSLCommerz', 'Completed', 'Pending', 'SSL_6848ecdfa2def', NULL, NULL, 'Joy Saha', NULL, '6666666666', '18', NULL, 'CUMILLA', 'state', '3700', 'ryrtyr', NULL, '665555555555', '18', NULL, 'Lakshmipur', 'sratr', 'Ght', 0.00, '', 0.00, NULL, NULL, 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Bangladesh', NULL, NULL, NULL, NULL, NULL, NULL),
(2, 0, 'Joy Saha', 's@1.com', '2025-06-10 19:43:56', '', 119, '', '', '', '', '', 'Cash on Delivery', 'Completed', 'Pending', 'COD-1749609836-0', '', NULL, 'Joy Saha', NULL, '6666666666', '18', NULL, 'CUMILLA', 'state', '3700', 'ryrtyr', NULL, '665555555555', '18', NULL, 'Lakshmipur', 'sratr', 'Ght', 100.00, '', 0.00, NULL, 's@1.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Bangladesh', 's@1.com', NULL, NULL, NULL, NULL, NULL),
(3, 0, 'Joy Saha', 's@1.com', '2025-06-10 18:30:32', 'SSL_6848dc389c1ae', 379, '', '', '', '', '', 'SSLCommerz', 'Completed', 'Completed', 'SSL_6848dc389c1ae', NULL, NULL, 'Joy Saha', NULL, '6666666666', '18', NULL, 'CUMILLA', 'state', '3700', 'ryrtyr', NULL, '665555555555', '18', NULL, 'Lakshmipur', 'sratr', 'Ght', 0.00, '', 0.00, NULL, NULL, 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Bangladesh', NULL, NULL, NULL, NULL, NULL, NULL),
(4, 0, 'Joy Saha', 's@1.com', '2025-06-10 18:25:20', 'SSL_6848db0032183', 379, '', '', '', '', '', 'SSLCommerz', 'Completed', 'Completed', 'SSL_6848db0032183', NULL, NULL, 'Joy Saha', NULL, '6666666666', '18', NULL, 'CUMILLA', 'state', '3700', 'ryrtyr', NULL, '665555555555', '18', NULL, 'Lakshmipur', 'sratr', 'Ght', 0.00, '', 0.00, NULL, NULL, 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Bangladesh', NULL, NULL, NULL, NULL, NULL, NULL),
(5, 0, 'Joy Saha', 's@1.com', '2025-06-10 18:23:51', 'SSL_6848daa7dca39', 279, '', '', '', '', '', 'SSLCommerz', 'Completed', 'Completed', 'SSL_6848daa7dca39', NULL, NULL, 'Joy Saha', NULL, '6666666666', '18', NULL, 'CUMILLA', 'state', '3700', 'ryrtyr', NULL, '665555555555', '18', NULL, 'Lakshmipur', 'sratr', 'Ght', 0.00, '', 0.00, NULL, NULL, 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Bangladesh', NULL, NULL, NULL, NULL, NULL, NULL),
(6, 0, 'Joy Saha', 's@1.com', '2025-06-10 18:20:57', 'SSL_6848d9f9754a7', 167, '', '', '', '', '', 'SSLCommerz', 'Completed', 'Completed', 'SSL_6848d9f9754a7', NULL, NULL, 'Joy Saha', NULL, '6666666666', '18', NULL, 'CUMILLA', 'state', '3700', 'ryrtyr', NULL, '665555555555', '18', NULL, 'Lakshmipur', 'sratr', 'Ght', 0.00, '', 0.00, NULL, NULL, 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Bangladesh', NULL, NULL, NULL, NULL, NULL, NULL),
(7, 0, 'Joy Saha', 's@1.com', '2025-06-10 18:18:37', 'SSL_6848d96da530d', 269, '', '', '', '', '', 'SSLCommerz', 'Completed', 'Completed', 'SSL_6848d96da530d', NULL, NULL, 'Joy Saha', NULL, '6666666666', '18', NULL, 'CUMILLA', 'state', '3700', 'ryrtyr', NULL, '665555555555', '18', NULL, 'Lakshmipur', 'sratr', 'Ght', 0.00, '', 0.00, NULL, NULL, 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Bangladesh', NULL, NULL, NULL, NULL, NULL, NULL),
(8, 0, 'Joy Saha', 's@1.com', '2025-06-10 17:54:17', 'SSL_6848d3b9a569b', 379, '', '', '', '', '', 'SSLCommerz', 'Completed', 'Completed', 'SSL_6848d3b9a569b', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0.00, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL),
(43, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 00:27:15', 'SSL_6918396315102', 132, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6918396315102', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(47, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 01:40:13', 'SSL_69184a7d73ad4', 249, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Failed', 'Pending', 'SSL_69184a7d73ad4', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(48, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 01:41:52', 'SSL_69184ae0aaa50', 292, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Failed', 'Pending', 'SSL_69184ae0aaa50', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(49, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 01:45:39', 'SSL_69184bc3574ea', 249, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Failed', 'Pending', 'SSL_69184bc3574ea', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(50, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 01:48:38', 'SSL_69184c769fc50', 279, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Failed', 'Pending', 'SSL_69184c769fc50', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(51, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 01:49:12', 'SSL_69184c98314da', 125, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Failed', 'Pending', 'SSL_69184c98314da', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(52, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 01:51:26', 'SSL_69184d1e5a34d', 119, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Failed', 'Pending', 'SSL_69184d1e5a34d', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(54, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 02:07:35', 'SSL_691850e745eeb', 139, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Failed', 'Pending', 'SSL_691850e745eeb', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(56, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 02:11:51', 'SSL_691851e733f52', 167, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Failed', 'Pending', 'SSL_691851e733f52', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(57, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 02:13:19', 'SSL_6918523f4f85c', 167, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6918523f4f85c', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(58, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 02:20:54', 'SSL_6918540650c1d', 143, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6918540650c1d', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(59, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 02:21:02', 'SSL_6918540e617f1', 143, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6918540e617f1', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(60, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 02:21:17', 'SSL_6918541dc7c00', 143, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6918541dc7c00', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(61, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 02:22:09', 'SSL_691854514b8c6', 143, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_691854514b8c6', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(62, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 02:30:06', 'SSL_6918562e7806e', 156, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6918562e7806e', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(63, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 07:06:23', 'SSL_691896efbcc46', 269, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_691896efbcc46', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(67, 23, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-06 10:09:54', 'SSL_695d4ff268c81', 6796, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_695d4ff268c81', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(65, 21, 'Joy Saha', 'jsha3741@gmail.com', '2025-11-15 07:10:27', 'SSL_691897e3982bd', 379, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_691897e3982bd', NULL, NULL, 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 'Joy Saha', NULL, '01863054816', '18', NULL, 'Lakshmipur', 'T', 'Ght', 0.00, '', 0.00, NULL, 'jsha3741@gmail.com', 'Bangladesh', 'Bangladesh', 'jsha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(69, 23, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-09 11:05:50', 'SSL_6961518ea8a29', 595, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6961518ea8a29', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(70, 23, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-09 11:15:34', 'SSL_696153d65e40b', 137, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_696153d65e40b', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(73, 23, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-19 00:46:56', 'SSL_696def80526b9', 249, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Cancelled', 'Cancelled', 'SSL_696def80526b9', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(74, 23, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-19 00:49:21', 'SSL_696df011cfab3', 268, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Cancelled', 'Cancelled', 'SSL_696df011cfab3', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(75, 23, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-19 00:56:49', '', 150, '', NULL, NULL, NULL, '', 'Cash on Delivery', 'Cancelled', 'Cancelled', 'COD-1768813009-233488', '', NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 100.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(76, 23, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-19 01:06:25', 'SSL_696df4119e8d2', 119, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_696df4119e8d2', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(77, 23, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-20 11:47:35', 'SSL_696fdbd7ac2c6', 132, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_696fdbd7ac2c6', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(78, 24, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-21 05:19:42', 'SSL_6970d26ee15bf', 475, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6970d26ee15bf', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(79, 24, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-22 00:16:04', 'SSL_6971dcc461c1e', 475, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6971dcc461c1e', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(80, 24, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-22 00:18:37', 'SSL_6971dd5de1748', 475, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6971dd5de1748', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(81, 24, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-22 00:18:54', 'SSL_6971dd6e9e31a', 475, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6971dd6e9e31a', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(82, 24, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-22 00:20:17', 'SSL_6971ddc16df98', 475, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6971ddc16df98', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(83, 24, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-22 00:23:03', 'SSL_6971de675c938', 475, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6971de675c938', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(84, 24, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-22 00:24:08', 'SSL_6971dea8351d0', 475, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6971dea8351d0', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(85, 24, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-22 00:31:14', 'SSL_6971e0520728e', 475, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6971e0520728e', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(86, 24, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-22 00:37:07', 'SSL_6971e1b30f688', 475, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6971e1b30f688', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(87, 24, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-22 00:38:20', 'SSL_6971e1fc8d952', 475, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6971e1fc8d952', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL),
(88, 24, 'Joy Saha', 'jsaha3741@gmail.com', '2026-01-22 01:03:39', 'SSL_6971e7ebca8c4', 475, NULL, NULL, NULL, NULL, NULL, 'SSLCommerz', 'Completed', 'Pending', 'SSL_6971e7ebca8c4', NULL, NULL, 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 'Joy Saha', NULL, '01735342839', '18', NULL, 'CUMILLA', 'Cfg', '3700', 0.00, '', 0.00, NULL, 'jsaha3741@gmail.com', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'Vill:Shomserabad, p.o: Lakshmipur Sadar-3700,', 'jsaha3741@gmail.com', NULL, NULL, NULL, NULL, NULL);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_photo`
--

CREATE TABLE `tbl_photo` (
  `id` int(11) NOT NULL,
  `caption` varchar(255) NOT NULL,
  `photo` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_photo`
--

INSERT INTO `tbl_photo` (`id`, `caption`, `photo`) VALUES
(1, 'Photo 1', 'photo-1.jpg'),
(2, 'Photo 2', 'photo-2.jpg'),
(3, 'Photo 3', 'photo-3.jpg'),
(4, 'Photo 4', 'photo-4.jpg'),
(5, 'Photo 5', 'photo-5.jpg'),
(6, 'Photo 6', 'photo-6.jpg');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_post`
--

CREATE TABLE `tbl_post` (
  `post_id` int(11) NOT NULL,
  `post_title` varchar(255) NOT NULL,
  `post_slug` varchar(255) NOT NULL,
  `post_content` text NOT NULL,
  `post_date` varchar(255) NOT NULL,
  `photo` varchar(255) NOT NULL,
  `category_id` int(11) NOT NULL,
  `total_view` int(11) NOT NULL,
  `meta_title` varchar(255) NOT NULL,
  `meta_keyword` text NOT NULL,
  `meta_description` text NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_post`
--

INSERT INTO `tbl_post` (`post_id`, `post_title`, `post_slug`, `post_content`, `post_date`, `photo`, `category_id`, `total_view`, `meta_title`, `meta_keyword`, `meta_description`) VALUES
(1, 'Cu vel choro exerci pri et oratio iisque', 'cu-vel-choro-exerci-pri-et-oratio-iisque', '<p>Lorem ipsum dolor sit amet, qui case probo velit no, an postea scaevola partiendo mei. Id mea fuisset perpetua referrentur. Ut everti ceteros mei, alii discere eum no, duo id malis iuvaret. Ad sint everti accusam vel, ea viderer suscipiantur pri. Brute option minimum in cum, ignota iuvaret an pro.</p>\r\n\r\n<p>Solum atqui intellegebat mea an. Ne ius alterum aliquam. Ea nec populo aliquid mentitum, vis in meliore atomorum, sanctus consequat vituperatoribus duo ea. Ad doctus pertinacia ius, virtute fuisset id has, eum ut modo principes. Qui eu labore adversarium, oporteat delicata qui ut, an qui meliore principes. Id aliquid dolorum nam.</p>\r\n\r\n<p>Reque pericula philosophia ut mei, volumus eligendi mandamus has an. In nobis consulatu pri, has at timeam scaevola, has simul quaeque et. Te nec sale accumsan. Dolorem prodesset efficiendi sea ea.</p>\r\n\r\n<p>Et habeo modus debitis pri, vel quis fierent albucius ne. Ea animal meliore usu, nec etiam dolorum atomorum at, nam in audire mandamus omittantur. Cu ius dicam officiis molestiae, mea volumus officiis cotidieque no. Ut vel possim interpretaris, idque probatus antiopam has ad. Facilisi qualisque te sea, no dolorum mnesarchum usu.</p>\r\n\r\n<p>Eum tota graeci impetus an, eirmod invenire rationibus ne mel. Ignota habemus eum ex, vis omnesque delicata perpetua an. Sit id modo invidunt sapientem, ne eum vocibus dolores phaedrum. Case praesent appellantur eu per.</p>\r\n', '05-09-2017', 'news-1.jpg', 3, 14, 'Cu vel choro exerci pri et oratio iisque', '', ''),
(2, 'Epicurei necessitatibus eu facilisi postulant ', 'epicurei-necessitatibus-eu-facilisi-postulant-', '<p>Lorem ipsum dolor sit amet, qui case probo velit no, an postea scaevola partiendo mei. Id mea fuisset perpetua referrentur. Ut everti ceteros mei, alii discere eum no, duo id malis iuvaret. Ad sint everti accusam vel, ea viderer suscipiantur pri. Brute option minimum in cum, ignota iuvaret an pro.</p>\r\n\r\n<p>Solum atqui intellegebat mea an. Ne ius alterum aliquam. Ea nec populo aliquid mentitum, vis in meliore atomorum, sanctus consequat vituperatoribus duo ea. Ad doctus pertinacia ius, virtute fuisset id has, eum ut modo principes. Qui eu labore adversarium, oporteat delicata qui ut, an qui meliore principes. Id aliquid dolorum nam.</p>\r\n\r\n<p>Reque pericula philosophia ut mei, volumus eligendi mandamus has an. In nobis consulatu pri, has at timeam scaevola, has simul quaeque et. Te nec sale accumsan. Dolorem prodesset efficiendi sea ea.</p>\r\n\r\n<p>Et habeo modus debitis pri, vel quis fierent albucius ne. Ea animal meliore usu, nec etiam dolorum atomorum at, nam in audire mandamus omittantur. Cu ius dicam officiis molestiae, mea volumus officiis cotidieque no. Ut vel possim interpretaris, idque probatus antiopam has ad. Facilisi qualisque te sea, no dolorum mnesarchum usu.</p>\r\n\r\n<p>Eum tota graeci impetus an, eirmod invenire rationibus ne mel. Ignota habemus eum ex, vis omnesque delicata perpetua an. Sit id modo invidunt sapientem, ne eum vocibus dolores phaedrum. Case praesent appellantur eu per.</p>\r\n', '05-09-2017', 'news-2.jpg', 3, 6, 'Epicurei necessitatibus eu facilisi postulant ', '', ''),
(3, 'Mei ut errem legimus periculis eos liber', 'mei-ut-errem-legimus-periculis-eos-liber', '<p>Lorem ipsum dolor sit amet, qui case probo velit no, an postea scaevola partiendo mei. Id mea fuisset perpetua referrentur. Ut everti ceteros mei, alii discere eum no, duo id malis iuvaret. Ad sint everti accusam vel, ea viderer suscipiantur pri. Brute option minimum in cum, ignota iuvaret an pro.</p>\r\n\r\n<p>Solum atqui intellegebat mea an. Ne ius alterum aliquam. Ea nec populo aliquid mentitum, vis in meliore atomorum, sanctus consequat vituperatoribus duo ea. Ad doctus pertinacia ius, virtute fuisset id has, eum ut modo principes. Qui eu labore adversarium, oporteat delicata qui ut, an qui meliore principes. Id aliquid dolorum nam.</p>\r\n\r\n<p>Reque pericula philosophia ut mei, volumus eligendi mandamus has an. In nobis consulatu pri, has at timeam scaevola, has simul quaeque et. Te nec sale accumsan. Dolorem prodesset efficiendi sea ea.</p>\r\n\r\n<p>Et habeo modus debitis pri, vel quis fierent albucius ne. Ea animal meliore usu, nec etiam dolorum atomorum at, nam in audire mandamus omittantur. Cu ius dicam officiis molestiae, mea volumus officiis cotidieque no. Ut vel possim interpretaris, idque probatus antiopam has ad. Facilisi qualisque te sea, no dolorum mnesarchum usu.</p>\r\n\r\n<p>Eum tota graeci impetus an, eirmod invenire rationibus ne mel. Ignota habemus eum ex, vis omnesque delicata perpetua an. Sit id modo invidunt sapientem, ne eum vocibus dolores phaedrum. Case praesent appellantur eu per.</p>\r\n', '05-09-2017', 'news-3.jpg', 3, 1, 'Mei ut errem legimus periculis eos liber', '', ''),
(4, 'Id pro unum pertinax oportere vel', 'id-pro-unum-pertinax-oportere-vel', '<p>Lorem ipsum dolor sit amet, qui case probo velit no, an postea scaevola partiendo mei. Id mea fuisset perpetua referrentur. Ut everti ceteros mei, alii discere eum no, duo id malis iuvaret. Ad sint everti accusam vel, ea viderer suscipiantur pri. Brute option minimum in cum, ignota iuvaret an pro.</p>\r\n\r\n<p>Solum atqui intellegebat mea an. Ne ius alterum aliquam. Ea nec populo aliquid mentitum, vis in meliore atomorum, sanctus consequat vituperatoribus duo ea. Ad doctus pertinacia ius, virtute fuisset id has, eum ut modo principes. Qui eu labore adversarium, oporteat delicata qui ut, an qui meliore principes. Id aliquid dolorum nam.</p>\r\n\r\n<p>Reque pericula philosophia ut mei, volumus eligendi mandamus has an. In nobis consulatu pri, has at timeam scaevola, has simul quaeque et. Te nec sale accumsan. Dolorem prodesset efficiendi sea ea.</p>\r\n\r\n<p>Et habeo modus debitis pri, vel quis fierent albucius ne. Ea animal meliore usu, nec etiam dolorum atomorum at, nam in audire mandamus omittantur. Cu ius dicam officiis molestiae, mea volumus officiis cotidieque no. Ut vel possim interpretaris, idque probatus antiopam has ad. Facilisi qualisque te sea, no dolorum mnesarchum usu.</p>\r\n\r\n<p>Eum tota graeci impetus an, eirmod invenire rationibus ne mel. Ignota habemus eum ex, vis omnesque delicata perpetua an. Sit id modo invidunt sapientem, ne eum vocibus dolores phaedrum. Case praesent appellantur eu per.</p>\r\n', '05-09-2017', 'news-4.jpg', 4, 0, 'Id pro unum pertinax oportere vel', '', ''),
(5, 'Tollit cetero cu usu etiam evertitur', 'tollit-cetero-cu-usu-etiam-evertitur', '<p>Lorem ipsum dolor sit amet, qui case probo velit no, an postea scaevola partiendo mei. Id mea fuisset perpetua referrentur. Ut everti ceteros mei, alii discere eum no, duo id malis iuvaret. Ad sint everti accusam vel, ea viderer suscipiantur pri. Brute option minimum in cum, ignota iuvaret an pro.</p>\r\n\r\n<p>Solum atqui intellegebat mea an. Ne ius alterum aliquam. Ea nec populo aliquid mentitum, vis in meliore atomorum, sanctus consequat vituperatoribus duo ea. Ad doctus pertinacia ius, virtute fuisset id has, eum ut modo principes. Qui eu labore adversarium, oporteat delicata qui ut, an qui meliore principes. Id aliquid dolorum nam.</p>\r\n\r\n<p>Reque pericula philosophia ut mei, volumus eligendi mandamus has an. In nobis consulatu pri, has at timeam scaevola, has simul quaeque et. Te nec sale accumsan. Dolorem prodesset efficiendi sea ea.</p>\r\n\r\n<p>Et habeo modus debitis pri, vel quis fierent albucius ne. Ea animal meliore usu, nec etiam dolorum atomorum at, nam in audire mandamus omittantur. Cu ius dicam officiis molestiae, mea volumus officiis cotidieque no. Ut vel possim interpretaris, idque probatus antiopam has ad. Facilisi qualisque te sea, no dolorum mnesarchum usu.</p>\r\n\r\n<p>Eum tota graeci impetus an, eirmod invenire rationibus ne mel. Ignota habemus eum ex, vis omnesque delicata perpetua an. Sit id modo invidunt sapientem, ne eum vocibus dolores phaedrum. Case praesent appellantur eu per.</p>\r\n', '05-09-2017', 'news-5.jpg', 4, 24, 'Tollit cetero cu usu etiam evertitur', '', ''),
(6, 'Omnes ornatus qui et te aeterno', 'omnes-ornatus-qui-et-te-aeterno', '<p>Lorem ipsum dolor sit amet, qui case probo velit no, an postea scaevola partiendo mei. Id mea fuisset perpetua referrentur. Ut everti ceteros mei, alii discere eum no, duo id malis iuvaret. Ad sint everti accusam vel, ea viderer suscipiantur pri. Brute option minimum in cum, ignota iuvaret an pro.</p>\r\n\r\n<p>Solum atqui intellegebat mea an. Ne ius alterum aliquam. Ea nec populo aliquid mentitum, vis in meliore atomorum, sanctus consequat vituperatoribus duo ea. Ad doctus pertinacia ius, virtute fuisset id has, eum ut modo principes. Qui eu labore adversarium, oporteat delicata qui ut, an qui meliore principes. Id aliquid dolorum nam.</p>\r\n\r\n<p>Reque pericula philosophia ut mei, volumus eligendi mandamus has an. In nobis consulatu pri, has at timeam scaevola, has simul quaeque et. Te nec sale accumsan. Dolorem prodesset efficiendi sea ea.</p>\r\n\r\n<p>Et habeo modus debitis pri, vel quis fierent albucius ne. Ea animal meliore usu, nec etiam dolorum atomorum at, nam in audire mandamus omittantur. Cu ius dicam officiis molestiae, mea volumus officiis cotidieque no. Ut vel possim interpretaris, idque probatus antiopam has ad. Facilisi qualisque te sea, no dolorum mnesarchum usu.</p>\r\n\r\n<p>Eum tota graeci impetus an, eirmod invenire rationibus ne mel. Ignota habemus eum ex, vis omnesque delicata perpetua an. Sit id modo invidunt sapientem, ne eum vocibus dolores phaedrum. Case praesent appellantur eu per.</p>\r\n', '05-09-2017', 'news-6.jpg', 4, 2, 'Omnes ornatus qui et te aeterno', '', ''),
(7, 'Vix tale noluisse voluptua ad ne', 'vix-tale-noluisse-voluptua-ad-ne', '<p>Lorem ipsum dolor sit amet, qui case probo velit no, an postea scaevola partiendo mei. Id mea fuisset perpetua referrentur. Ut everti ceteros mei, alii discere eum no, duo id malis iuvaret. Ad sint everti accusam vel, ea viderer suscipiantur pri. Brute option minimum in cum, ignota iuvaret an pro.</p>\r\n\r\n<p>Solum atqui intellegebat mea an. Ne ius alterum aliquam. Ea nec populo aliquid mentitum, vis in meliore atomorum, sanctus consequat vituperatoribus duo ea. Ad doctus pertinacia ius, virtute fuisset id has, eum ut modo principes. Qui eu labore adversarium, oporteat delicata qui ut, an qui meliore principes. Id aliquid dolorum nam.</p>\r\n\r\n<p>Reque pericula philosophia ut mei, volumus eligendi mandamus has an. In nobis consulatu pri, has at timeam scaevola, has simul quaeque et. Te nec sale accumsan. Dolorem prodesset efficiendi sea ea.</p>\r\n\r\n<p>Et habeo modus debitis pri, vel quis fierent albucius ne. Ea animal meliore usu, nec etiam dolorum atomorum at, nam in audire mandamus omittantur. Cu ius dicam officiis molestiae, mea volumus officiis cotidieque no. Ut vel possim interpretaris, idque probatus antiopam has ad. Facilisi qualisque te sea, no dolorum mnesarchum usu.</p>\r\n\r\n<p>Eum tota graeci impetus an, eirmod invenire rationibus ne mel. Ignota habemus eum ex, vis omnesque delicata perpetua an. Sit id modo invidunt sapientem, ne eum vocibus dolores phaedrum. Case praesent appellantur eu per.</p>\r\n', '05-09-2017', 'news-7.jpg', 2, 0, 'Vix tale noluisse voluptua ad ne', '', ''),
(8, 'Liber utroque vim an ne his brute', 'liber-utroque-vim-an-ne-his-brute', '<p>Lorem ipsum dolor sit amet, qui case probo velit no, an postea scaevola partiendo mei. Id mea fuisset perpetua referrentur. Ut everti ceteros mei, alii discere eum no, duo id malis iuvaret. Ad sint everti accusam vel, ea viderer suscipiantur pri. Brute option minimum in cum, ignota iuvaret an pro.</p>\r\n\r\n<p>Solum atqui intellegebat mea an. Ne ius alterum aliquam. Ea nec populo aliquid mentitum, vis in meliore atomorum, sanctus consequat vituperatoribus duo ea. Ad doctus pertinacia ius, virtute fuisset id has, eum ut modo principes. Qui eu labore adversarium, oporteat delicata qui ut, an qui meliore principes. Id aliquid dolorum nam.</p>\r\n\r\n<p>Reque pericula philosophia ut mei, volumus eligendi mandamus has an. In nobis consulatu pri, has at timeam scaevola, has simul quaeque et. Te nec sale accumsan. Dolorem prodesset efficiendi sea ea.</p>\r\n\r\n<p>Et habeo modus debitis pri, vel quis fierent albucius ne. Ea animal meliore usu, nec etiam dolorum atomorum at, nam in audire mandamus omittantur. Cu ius dicam officiis molestiae, mea volumus officiis cotidieque no. Ut vel possim interpretaris, idque probatus antiopam has ad. Facilisi qualisque te sea, no dolorum mnesarchum usu.</p>\r\n\r\n<p>Eum tota graeci impetus an, eirmod invenire rationibus ne mel. Ignota habemus eum ex, vis omnesque delicata perpetua an. Sit id modo invidunt sapientem, ne eum vocibus dolores phaedrum. Case praesent appellantur eu per.</p>\r\n', '05-09-2017', 'news-8.jpg', 2, 12, 'Liber utroque vim an ne his brute', '', ''),
(9, 'Nostrum copiosae argumentum has', 'nostrum-copiosae-argumentum-has', '<p>Lorem ipsum dolor sit amet, qui case probo velit no, an postea scaevola partiendo mei. Id mea fuisset perpetua referrentur. Ut everti ceteros mei, alii discere eum no, duo id malis iuvaret. Ad sint everti accusam vel, ea viderer suscipiantur pri. Brute option minimum in cum, ignota iuvaret an pro.</p>\r\n\r\n<p>Solum atqui intellegebat mea an. Ne ius alterum aliquam. Ea nec populo aliquid mentitum, vis in meliore atomorum, sanctus consequat vituperatoribus duo ea. Ad doctus pertinacia ius, virtute fuisset id has, eum ut modo principes. Qui eu labore adversarium, oporteat delicata qui ut, an qui meliore principes. Id aliquid dolorum nam.</p>\r\n\r\n<p>Reque pericula philosophia ut mei, volumus eligendi mandamus has an. In nobis consulatu pri, has at timeam scaevola, has simul quaeque et. Te nec sale accumsan. Dolorem prodesset efficiendi sea ea.</p>\r\n\r\n<p>Et habeo modus debitis pri, vel quis fierent albucius ne. Ea animal meliore usu, nec etiam dolorum atomorum at, nam in audire mandamus omittantur. Cu ius dicam officiis molestiae, mea volumus officiis cotidieque no. Ut vel possim interpretaris, idque probatus antiopam has ad. Facilisi qualisque te sea, no dolorum mnesarchum usu.</p>\r\n\r\n<p>Eum tota graeci impetus an, eirmod invenire rationibus ne mel. Ignota habemus eum ex, vis omnesque delicata perpetua an. Sit id modo invidunt sapientem, ne eum vocibus dolores phaedrum. Case praesent appellantur eu per.</p>\r\n', '05-09-2017', 'news-9.jpg', 1, 12, 'Nostrum copiosae argumentum has', '', ''),
(10, 'An labores explicari qui eu', 'an-labores-explicari-qui-eu', '<p>Lorem ipsum dolor sit amet, qui case probo velit no, an postea scaevola partiendo mei. Id mea fuisset perpetua referrentur. Ut everti ceteros mei, alii discere eum no, duo id malis iuvaret. Ad sint everti accusam vel, ea viderer suscipiantur pri. Brute option minimum in cum, ignota iuvaret an pro.</p>\r\n\r\n<p>Solum atqui intellegebat mea an. Ne ius alterum aliquam. Ea nec populo aliquid mentitum, vis in meliore atomorum, sanctus consequat vituperatoribus duo ea. Ad doctus pertinacia ius, virtute fuisset id has, eum ut modo principes. Qui eu labore adversarium, oporteat delicata qui ut, an qui meliore principes. Id aliquid dolorum nam.</p>\r\n\r\n<p>Reque pericula philosophia ut mei, volumus eligendi mandamus has an. In nobis consulatu pri, has at timeam scaevola, has simul quaeque et. Te nec sale accumsan. Dolorem prodesset efficiendi sea ea.</p>\r\n\r\n<p>Et habeo modus debitis pri, vel quis fierent albucius ne. Ea animal meliore usu, nec etiam dolorum atomorum at, nam in audire mandamus omittantur. Cu ius dicam officiis molestiae, mea volumus officiis cotidieque no. Ut vel possim interpretaris, idque probatus antiopam has ad. Facilisi qualisque te sea, no dolorum mnesarchum usu.</p>\r\n\r\n<p>Eum tota graeci impetus an, eirmod invenire rationibus ne mel. Ignota habemus eum ex, vis omnesque delicata perpetua an. Sit id modo invidunt sapientem, ne eum vocibus dolores phaedrum. Case praesent appellantur eu per.</p>\r\n', '05-09-2017', 'news-10.jpg', 1, 4, 'An labores explicari qui eu', '', ''),
(11, 'Lorem ipsum dolor sit amet', 'lorem-ipsum-dolor-sit-amet', '<p>Lorem ipsum dolor sit amet, qui case probo velit no, an postea scaevola partiendo mei. Id mea fuisset perpetua referrentur. Ut everti ceteros mei, alii discere eum no, duo id malis iuvaret. Ad sint everti accusam vel, ea viderer suscipiantur pri. Brute option minimum in cum, ignota iuvaret an pro.</p>\r\n\r\n<p>Solum atqui intellegebat mea an. Ne ius alterum aliquam. Ea nec populo aliquid mentitum, vis in meliore atomorum, sanctus consequat vituperatoribus duo ea. Ad doctus pertinacia ius, virtute fuisset id has, eum ut modo principes. Qui eu labore adversarium, oporteat delicata qui ut, an qui meliore principes. Id aliquid dolorum nam.</p>\r\n\r\n<p>Reque pericula philosophia ut mei, volumus eligendi mandamus has an. In nobis consulatu pri, has at timeam scaevola, has simul quaeque et. Te nec sale accumsan. Dolorem prodesset efficiendi sea ea.</p>\r\n\r\n<p>Et habeo modus debitis pri, vel quis fierent albucius ne. Ea animal meliore usu, nec etiam dolorum atomorum at, nam in audire mandamus omittantur. Cu ius dicam officiis molestiae, mea volumus officiis cotidieque no. Ut vel possim interpretaris, idque probatus antiopam has ad. Facilisi qualisque te sea, no dolorum mnesarchum usu.</p>\r\n\r\n<p>Eum tota graeci impetus an, eirmod invenire rationibus ne mel. Ignota habemus eum ex, vis omnesque delicata perpetua an. Sit id modo invidunt sapientem, ne eum vocibus dolores phaedrum. Case praesent appellantur eu per.</p>\r\n', '05-09-2017', 'news-11.jpg', 1, 18, 'Lorem ipsum dolor sit amet', '', '');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_product`
--

CREATE TABLE `tbl_product` (
  `p_id` int(11) NOT NULL,
  `business_id` int(11) DEFAULT NULL,
  `p_name` varchar(255) NOT NULL,
  `p_old_price` varchar(10) NOT NULL,
  `p_current_price` varchar(10) NOT NULL,
  `p_qty` int(10) NOT NULL,
  `p_featured_photo` varchar(255) NOT NULL,
  `p_description` text NOT NULL,
  `p_short_description` text NOT NULL,
  `p_feature` text NOT NULL,
  `p_condition` text NOT NULL,
  `p_return_policy` text NOT NULL,
  `p_total_view` int(11) NOT NULL,
  `p_is_featured` int(1) NOT NULL,
  `p_is_active` int(1) NOT NULL,
  `ecat_id` int(11) NOT NULL,
  `p_video_link` varchar(255) DEFAULT '',
  `is_top_sale` tinyint(1) DEFAULT 0,
  `is_free_shipping` tinyint(1) DEFAULT 0,
  `is_official` tinyint(1) DEFAULT 0,
  `is_premium` tinyint(1) DEFAULT 0,
  `is_overseas` tinyint(1) DEFAULT 0,
  `is_max_vouchered` tinyint(1) DEFAULT 0,
  `vendor_id` int(11) DEFAULT 0,
  `allow_coin_payment` tinyint(1) DEFAULT 0,
  `is_coin_buyable` tinyint(1) DEFAULT 0,
  `coin_price` int(11) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_product`
--

INSERT INTO `tbl_product` (`p_id`, `business_id`, `p_name`, `p_old_price`, `p_current_price`, `p_qty`, `p_featured_photo`, `p_description`, `p_short_description`, `p_feature`, `p_condition`, `p_return_policy`, `p_total_view`, `p_is_featured`, `p_is_active`, `ecat_id`, `p_video_link`, `is_top_sale`, `is_free_shipping`, `is_official`, `is_premium`, `is_overseas`, `is_max_vouchered`, `vendor_id`, `allow_coin_payment`, `is_coin_buyable`, `coin_price`) VALUES
(0, NULL, 'op', '100.00', '55.00', 1, 'product-featured-.jpg', '<p>kk</p>', '<p><b>bbb</b></p>', '', '', '', 1, 1, 1, 1, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(83, NULL, 'Men\'s Ultra Cotton T-Shirt, Multipack', '26', '19', 73, 'product-featured-83.jpg', '<p style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Solids: 100% Cotton; Sport Grey And Antique Heather: 90% Cotton, 10% Polyester; Safety Colors And Heather: 50% Cotton, 50% Polyester.</span></p><p style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Available in 2 packs and a wide array of colors so you can stock up on your favorite.</span></p>', '<p><span style=\"color: rgb(15, 17, 17); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\">Style 20020-Multipack; Solids: 100% Cotton.</span><br></p>', '<ul><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block; font-family: Arial;\">Pull On closure</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block; font-family: Arial;\">Machine Wash</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block; font-family: Arial;\">Longer dropped shoulder, straighter armhole, and wider, shorter sleeves</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block; font-family: Arial;\">Double-stitching at the hems to make it built to last</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block; font-family: Arial;\">Thick and hefty fabric</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block; font-family: Arial;\">Tear away tag</span></li></ul>', '<p>This is a sample text for conditions.</p>', '<p><span style=\"color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 67, 0, 1, 21, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(84, NULL, 'Loose-fit One-Shoulder Cutout Rib Knit Maxi Dress', '51', '39', 11, 'product-featured-84.jpg', 'A&nbsp;<span style=\"color: rgb(51, 51, 51); font-family: &quot;Amazon Ember&quot;, Arial, sans-serif; font-size: small;\">source for must-have style inspiration from global influencers. Shop limited-edition collections and discover chic wardrobe essentials. Look out for trend inspiration, exclusive brand collaborations, and expert styling tips from those in the know.</span>', '<p style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">86% Tencel, 14% Elastane</span></p>', '<ul><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Machine Wash</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Staples by The Drop</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">This maxi dress measures 48\"/122 cm long</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Loose-Fit: designed for comfort</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">A subtle twist cutout adds a touch of romance to this minimal one-shoulder flowy maxi dress silhouette, cut from a lightweight Tencel blend ribbed knit. Belt the waist for added shape and a pop of color</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers a&nbsp;</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">&nbsp;in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 133, 1, 1, 32, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(85, NULL, 'Men\'s Soft Classic Sneaker', '110', '91', 31, 'product-featured-85.jpg', '<p><span style=\"color: rgb(51, 51, 51); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: small;\">Brings a formal leather finish to a laidback silhouette in a shoe that delivers equally on quality and comfort The metal eyelets and contrasting heel patch balance out the style\'s sleek uniformity Wear yours with jeans an Oxford shirt and a blazer.</span><br></p>', '<p style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Synthetic sole, Secure fit.</span></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Crafted in either hand-finished crust leather or brushed nubuck made in our own tanneries</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Textile lining and molded removable insole offer softness and breathability</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Lightweight sole provides cushioning grip and flexibility using innovative ECCO FluidForm Direct Comfort Technology</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Textile laces are easily adjusted for a secure fit</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">A full anatomical last shape provides a supremely comfortable fit</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 12, 0, 1, 25, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(86, NULL, 'Amazfit GTS 3 Smart Watch for Android iPhone', '199', '179', 14, 'product-featured-86.jpg', '<p style=\"padding: 0px; margin-top: 0px; text-rendering: optimizelegibility; margin-bottom: 0px !important; line-height: 32px !important;\">Amazfit GTS 3 is the most powerful, easy-to-use smartwatch that combining cutting-edge health &amp; fitness features and a fashionable slim &amp; light design. The smartwatch adopts a 1.75-inch ultra HD AMOLED display which has increased by 14% compared with the previous generation and boasts a 72.4% screen-to-body ratio that\'s among the highest in the smartwatch industry. Match your mood, an outfit or the occasion with a wide selection of more than 100 stylish watch faces - or even upload your own photo as the background image for true personalization. Thanks to the advanced 6PD (six photodiodes) BioTrackerâ„¢ PPG 3.0 biometric sensor, GTS 3 can track your heart rate, blood-oxygen saturation, stress level and breathing rate in one single tap of the watch, for a result in as little as 45 seconds. And Its health management features also includes in-depth monitoring of sleep &amp; sleep breathing quality and female cycle tracking. This sports watch is your next-level fitness partner with 150+ sports modes, smart recognition of 8 sports, and a water-resistance grade of 5 ATM. Comes with Alexa built-in and an offline voice assistant to liberate your hands, and supports GPS, GLONASS, Galileo, BDS and QZSS satellite navigation systems to accurately track your route. Super endurance that won\'t let you down, it can last for up to 12 days with typical usage and 20 days with battery saver mode. Compatible with Android 7.0 and above, iOS 12.0 and above device.<br></p>', '<p style=\"padding: 0px; margin-top: 0px; text-rendering: optimizelegibility; margin-bottom: 0px !important; line-height: 32px !important;\"><span id=\"productTitle\" class=\"a-size-large product-title-word-break\" style=\"text-rendering: optimizelegibility; word-break: break-word; line-height: 32px !important; font-family: Roboto;\">Alexa Built-in, GPS Fitness Sports Watch with 150 Sports Modes, 1.75â€ AMOLED Display, 12-Day Battery Life, Blood Oxygen Heart Rate Tracking</span></p>', '<ul><li>Smart 24H Monitoring of Blood-oxygen Levels</li><li>Monitor Heart Rate All Day &amp; While Swimming</li><li>A Simple Health Overview with PAI Health Assessment</li><li>In-depth Monitoring of Sleep &amp; Sleep Breathing Quality</li><li>Stress Level Monitoring &amp; Measurement</li><li>Female Cycle Tracking</li><li><span style=\"color: rgb(15, 17, 17); font-family: &quot;Amazon Ember&quot;, Arial, sans-serif; font-size: 14px;\">Alexa Built in</span></li><li><span style=\"color: rgb(15, 17, 17); font-family: &quot;Amazon Ember&quot;, Arial, sans-serif; font-size: 14px;\">12-Day battery life<br></span><span style=\"color: rgb(15, 17, 17); font-family: &quot;Amazon Ember&quot;, Arial, sans-serif; font-size: 14px; font-weight: 700;\"><br></span><span style=\"color: rgb(15, 17, 17); font-family: &quot;Amazon Ember&quot;, Arial, sans-serif; font-size: 14px; font-weight: 700;\"><br></span><br></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers a&nbsp;</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">&nbsp;in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 119, 1, 1, 3, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(87, NULL, 'Truck Boys Pajamas Toddler Sleepwear Clothes', '59', '37', 56, 'product-featured-87.jpg', '<p style=\"padding: 0px; margin-bottom: 14px; color: rgb(15, 17, 17); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><span class=\"a-text-bold\" style=\"\">Airplane Pajamas:</span></p><p style=\"padding: 0px; margin-bottom: 14px; color: rgb(15, 17, 17); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\">Wide crew neckline, elastic waistband and stretchable material make them easily skip on/off. Breathable, moisture absorbent material and pants set design help kids to lose heat faster and keep warm in cool day</p><p style=\"padding: 0px; margin-bottom: 14px; color: rgb(15, 17, 17); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\">Great set with colorful cartoon pattern, sporty and stylish, suitable for both sleepwear and daily wear, especially for school pajamas day</p>', 'T shirt Pants set for Kids Size 1Y - 14Y', '<p></p><p><ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><ul></ul></ul><ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><ul></ul></ul><ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><ul></ul></ul><ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><ul></ul></ul><ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><ul></ul></ul></p><ul><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">SUPER COZY PAJAMAS SET - Both the top and bottoms are made of 100% natural cotton, extremely soft, comfortable and keep cool in summer</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">ADORABLE PATTERN - Super cool airplane and space cartoon pattern on the top, a lot of little airplane prints on the bottom, boys favorite</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">CONSIDERATE DESIGN - Casual wide neckline, heat-sale label at the collar, relaxed straight legs, allow for unrestricted movement and a better sleep</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">EASY CARE - As the durable and high quality material, simply machine wash or hand wash in mild water</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">SNUGLY FITTED - As the snugly fitted design and shrinkable cotton material, youÂ¡Â¯d better consider one or two bigger size</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 26, 0, 1, 26, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(88, NULL, 'Under Armour Men\'s Sportstyle Left Chest Short Sleeve T-shirt', '108', '83', 59, 'product-featured-88.jpg', '<p><span style=\"color: rgb(51, 51, 51); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: small;\">Super-soft, cotton-blend fabric provides all-day comfort.</span><br></p>', '<p style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Loose:Â Fuller cut for complete comfort.</span></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Super-soft, cotton-blend fabric provides all-day comfort</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Loose:Â Fuller cut for complete comfort.</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 5, 0, 1, 21, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(89, NULL, 'Men\'s Fleece Jogger Pant', '58', '37', 110, 'product-featured-89.jpg', '<p><span style=\"color: rgb(51, 51, 51); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: small;\">A relaxed leg and elastic waistband bring lounge-ready style to this classic casual pant</span><br></p>', '<p style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">A relaxed leg and elastic, drawstring waistband bring lounge-ready style to this classic casual pant</span></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Elastic cuffs at ankle and on-seam side pockets</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Everyday made better: we listen to customer feedback and fine-tune every detail to ensure quality, fit, and comfort</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 38, 0, 1, 18, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(90, NULL, 'Women\'s Thin Cotton Zip Up Hoodie Jacket', '43', '32', 62, 'product-featured-90.jpg', '<p><span style=\"color: rgb(15, 17, 17); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\">Made with quality thin cotton material, this casual style zip-up hoodie is perfect for when you want extra protection without having a bulky jacket on or to keep up with your active lifestyle. Comfortable, flattering, and functional. Itâ€™s perfect for when you need to get things done.</span><br></p>', '<p>CASUAL & COMFY<br></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Full zip up closure with pockets</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">The perfect year-long hoodie</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Uniquely thin design</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Pullover Series also available</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Model is 5\' 7\" with 34-25-36 measurement.</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 57, 0, 1, 14, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(91, NULL, 'Women\'s Oversized Fleece Hoodie', '68', '56', 40, 'product-featured-91.jpg', '<p style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Oversized silhouette for maximum comfort and quality layering</span></p>', '<p><span style=\"color: rgb(51, 51, 51); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: small;\">Built for her lifestyle.</span><br></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Oversized silhouette for maximum comfort and quality layering</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Soft and warm fleece for ultimate comfort and wearability</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 28, 0, 1, 14, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(92, NULL, 'Travelpro Laptop Carry-on Travel Tote Bag', '110', '91', 29, 'product-featured-92.jpg', '<p><span style=\"color: rgb(51, 51, 51); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: small;\">Everything she needs in one perfect bag! It delivers big on performance in a compact, lightweight carry-on. Organization is effortless with interior pockets for everything from power cords and back-up batteries to cosmetics and accessories. Padded laptop and tablet sleeves keep your electronics protected, while a quick-access front pocket with magnetic closure is ideal for storing a cell phone, keys or other necessities. There is even a side pocket perfect for a water bottle, compact umbrella, gloves or other convenience. A rear strap lets you Stack this bag on a spinner or Rollaboard for hands-free mobility.</span><br></p>', '<p>Padded laptop (up to 14â€) and tablet sleeves offer protection for electronics.<br></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Polyester</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Imported</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Padded laptop (up to 14â€) and tablet sleeves offer protection for electronics. Organizational pockets store power cords, powerbanks and other essentials</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Quick access, magnetic front pocket is ideal for storing a cell phone or other essentials. Exterior side pocket holds a water bottle, compact umbrella or other accessories</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Polyester fabric with DuraGuard coating resists water and stains to keep luggage looking great. Unobtrusive rear strap fits around the extension handle of a Rollaboard or spinner suitcase for secure stacking and hands-free mobility</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Ergonomic, high-tensile-strength zipper pulls are tough yet easy on the hands</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Backed by Travelpro Built For A Lifetime Limited Warranty. Dimensions: 11 x 21x 5 inches weight: 1.4 lbs</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 18, 0, 1, 60, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(93, NULL, 'Gold Plated Leopard Print Crystal Big Round Hoop Earrings', '32', '25', 162, 'product-featured-93.jpg', '<p><span style=\"color: rgb(51, 51, 51); font-family: &quot;Amazon Ember&quot;, Arial, sans-serif; font-size: small;\">These beautiful 18k gold filled dangle earrings sparkle with stunning created ruby center stones surrounded by a shining Cubic Zirconia halo. These extravagant earrings are the perfect anniversary or birthday gift for your special!</span><br></p>', '<p style=\"padding: 0px; margin-top: 0px; text-rendering: optimizelegibility; margin-bottom: 0px !important; line-height: 32px !important;\"><span id=\"productTitle\" class=\"a-size-large product-title-word-break\" style=\"text-rendering: optimizelegibility; word-break: break-word; line-height: 32px !important; font-family: Roboto;\">Gm148 2\" inches</span></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: &quot;Amazon Ember&quot;, Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Width: 6mm</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Diamter: 2 inches</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers a&nbsp;</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">&nbsp;in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 18, 0, 1, 42, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(94, NULL, 'WD 5TB Elements Portable External Hard Drive HDD', '160', '149', 41, 'product-featured-94.jpg', '<p><span style=\"color: rgb(51, 51, 51); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: small;\">Western Digital elements portable hard drives offer reliable, high-capacity storage, fast data transfer rates and universal connectivity with USB 3.0 and USB 2.0 devices to back up your photos, videos and files on the go. The small, lightweight design offers up to 5TB capacity.</span><br></p>', '<p style=\"padding: 0px; margin-top: 0px; text-rendering: optimizelegibility; margin-bottom: 0px !important; line-height: 32px !important;\"><span id=\"productTitle\" class=\"a-size-large product-title-word-break\" style=\"text-rendering: optimizelegibility; word-break: break-word; line-height: 32px !important;\">USB 3.0, Compatible with PC, Mac, PS4 & Xbox - WDBU6Y0050BBK-WESN</span></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">USB 3.0 and USB 2.0 Compatibility</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Compatible with PC, Mac, PS4 & Xbox</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Fast Data Transfers, Improve PC Performance</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">High Capacity</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Memory Storage Capacity: 5TB</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 23, 0, 1, 71, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(95, NULL, 'Bose QuietComfort 45 Bluetooth Wireless Headphones', '329', '279', 18, 'product-featured-95.jpg', '<p><span style=\"color: rgb(51, 51, 51); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: small;\">The first noise cancelling headphones are back, with world-class quiet, lightweight materials, and proprietary technology for deep, clear sound. Bose QuietComfort 45 headphones arenâ€™t just an icon reborn â€“ theyâ€™re the perfect balance of quiet, comfort, and sound. Plus, everything that made the first around ear headphones an icon is still here. Just refined. Like an updated design with smooth cushions and a clean look. Plush synthetic leather, impact-resistant glass-filled nylon, and cast-metal hinges were all selected for their comfort as well as their durability. Add in minimal clamping force, and youâ€™ll almost forget youâ€™re wearing Bluetooth wireless noise cancelling headphones.</span><br></p>', 'Iconic, Quiet, Comfort and Sound.', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Noise cancelling wireless headphones â€“ The perfect balance of quiet, comfort, and sound. Bose uses tiny mics to measure, compare, and react to outside noise, cancelling it with opposite signals.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">High-fidelity audio â€“ The TriPort acoustic architecture offers depth and fullness. Volume-optimized Active EQ maintains balanced performance at any volume, so bass stays consistent when turned down and the music remains clear when turned up.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Quiet and Aware Modes â€“ Choose Quiet Mode for full noise cancelling, or Aware Mode to bring the outside into the around ear headphones and hear your environment and your music at the same time.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Over ear headphones â€“ These comfortable wireless headphones are suitable for all-day wear. Crafted with plush synthetic leather and impact-resistant nylon, and designed with minimal clamping force, theyâ€™re as luxurious as they are durable.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Up to 24 hours battery life â€“ Enjoy 24 hours of battery life from a single charge. A quick 15-minute charge offers 3 hours when youâ€™re on the go, or plug in the included audio cable to listen for even longer in wired mode.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Around ear headphones charge via USB-C â€“ Headphones charge via the included USB-C cable.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Bluetooth wireless noise cancelling headphones â€“ These headphones are optimized for a strong, reliable Bluetooth connection within 30 feet of the paired device.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Bose Music app â€“ The app walks you through guided setup of your over ear headphones, making it easy to get started. Plus, access adjustable noise cancellation settings, manage your Bluetooth connections, enable shortcuts, and more.</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 127, 1, 1, 62, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(97, NULL, 'Women\'s Tea Length Dress with Rosette Detail (Petite & Regular)', '87', '67', 47, 'product-featured-97.jpg', '<p><span style=\"color: rgb(51, 51, 51); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: small;\">Our t-length party dress has a solid stretch top, full soutache sequin skirt and tie belt that is perfect for any evening event.</span><br></p>', '<p style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Short-sleeve v-neck midi blue dress</span></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Sequin detail</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">This style is available in Regular and Plus Size on Amazon.com</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Center back zip</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Joanna Chen design</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Short-sleeve v-neck midi blue dress</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 49, 1, 1, 32, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(98, NULL, 'Women\'s Fuzzy Fleece Lapel Open Front Long Cardigan Coat', '52', '43', 36, 'product-featured-98.jpg', '<h3 class=\"a-spacing-mini\" style=\"padding: 0px; margin-top: 0px; margin-right: 0px; margin-left: 0px; text-rendering: optimizelegibility; font-weight: bold; font-size: 17px; line-height: 1.255; font-family: \"Amazon Ember\", Arial, sans-serif; color: rgb(15, 17, 17); margin-bottom: 6px !important;\">Design Details - Women Fuzzy Winter Teddy Coat</h3><p class=\"a-spacing-base\" style=\"padding: 0px; color: rgb(15, 17, 17); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px; margin-bottom: 14px !important;\"></p><ul class=\"a-unordered-list a-vertical\" style=\"margin-right: 0px; margin-bottom: 18px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\"><span class=\"a-text-bold\" style=\"font-weight: 700 !important;\">Material:</span>Â 85% Cotton + 15% Spandex. This women\'s teddy coat is 100% brand new and high quality!</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\"><span class=\"a-text-bold\" style=\"font-weight: 700 !important;\">Style:</span>Â Causal, Long Sleeves, Knee Length, Fuzzy, Faux Fur, Lapel, Open Front, this women\'s teddy coat can be both chic and warm.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\"><span class=\"a-text-bold\" style=\"font-weight: 700 !important;\">Occasion:</span>Â Spring, Fall, Winter, Work, Date, Vacation, Daily Casual, At Home . This women\'s faux fur coat is suitable for both formal and casual occasions.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\"><span class=\"a-text-bold\" style=\"font-weight: 700 !important;\">Package include:</span>Â 1 Womens Fuzzy Coat</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\"><span class=\"a-text-bold\" style=\"font-weight: 700 !important;\">KINDLY NOTE: Different body types may have different fit from the model pictures, please refer to customer review images for more fitting information.</span></span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\"><span class=\"a-text-bold\" style=\"font-weight: 700 !important;\">KINDLY NOTE: this item is designed to be open front and has no button or zipper closure. Please take it into consideration before purchase.</span></span></li></ul>', '<p style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Material:85% Polyester; 15% Spandex. 100% brand new and high quality!</span></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">No closure closure</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Material:85% Polyester; 15% Spandex. 100% brand new and high quality!</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Style: Causal, Long Sleeves, Knee Length, Fuzzy, Faux Fur, Lapel, Open Front</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Occasion: Spring, Fall, Winter, Work, Date, Vacation, Daily Casual, At Home</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Pair with: This women\'s coat goes perfect with a shirt/sweater & jeans/leggings/palazoo pants underneath and with short boots.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">KINDLY NOTE: Different body types may have different fit from the model pictures, please refer to customer review images for more fitting information.</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 20, 1, 1, 15, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
INSERT INTO `tbl_product` (`p_id`, `business_id`, `p_name`, `p_old_price`, `p_current_price`, `p_qty`, `p_featured_photo`, `p_description`, `p_short_description`, `p_feature`, `p_condition`, `p_return_policy`, `p_total_view`, `p_is_featured`, `p_is_active`, `ecat_id`, `p_video_link`, `is_top_sale`, `is_free_shipping`, `is_official`, `is_premium`, `is_overseas`, `is_max_vouchered`, `vendor_id`, `allow_coin_payment`, `is_coin_buyable`, `coin_price`) VALUES
(99, NULL, 'Oculus Quest 2 - Advanced All-In-One Virtual Reality Headset', '512', '495', 46, 'product-featured-99.jpg', '<p><span style=\"color: rgb(51, 51, 51); font-family: &quot;Amazon Ember&quot;, Arial, sans-serif; font-size: small;\">Oculus quest 2 is our most advanced all-in-one VR system yet. Every detail has been engineered to make virtual worlds adapt to your movements, letting you explore awe-inspiring games and experiences with unparalleled freedom. No PC or Console required. Get the most out of each moment with blazing-fast performance and next-generation graphics. Stay focused with a stunning display that features 50% more pixels than the original quest. Or take a break from the action and grab front-row seats to live concerts, exclusive events and more. The redesigned touch controllers feature improved ergonomics and intuitive controls that transport your gestures, motions and actions directly into VR. You can even connect your VR headset to a gaming-compatible computer with an Oculus Link cable to access hundreds of PC VR games and experiences. Quest 2 also lets you bring your friends into the action. With live casting, you can share your VR experience with people around you. Or meet up with friends in virtual worlds to battle in multiplayer competitions or just spend some time together. With Oculus quest 2, there\'s no end in sight to what you can play, create and discover in virtual reality.</span><br></p>', '<p style=\"padding: 0px; margin-top: 0px; text-rendering: optimizelegibility; margin-bottom: 0px !important; line-height: 32px !important;\"><span id=\"productTitle\" class=\"a-size-large product-title-word-break\" style=\"text-rendering: optimizelegibility; word-break: break-word; line-height: 32px !important; font-family: Roboto;\">Advanced All-In-One Virtual Reality Headset</span></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: &quot;Amazon Ember&quot;, Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Next-level Hardware - Make every move count with a blazing-fast processor and our highest-resolution display</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">All-In-One Gaming - With backward compatibility, you can explore new titles and old favorites in the expansive Quest content library</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Immersive Entertainment - Get the best seat in the house to live concerts, groundbreaking films, exclusive events and more</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Easy Setup - Just open the box, set up with the smartphone app and jump into VR. No PC or console needed. Requires wireless internet access and the Oculus app (free download) to set up device</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Premium Display - Catch every detail with a stunning display that features 50% more pixels than the original Quest</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Ultimate Control - Redesigned Oculus Touch controllers transport your movements directly into VR with intuitive controls</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">PC VR Compatible - Step into incredible Oculus Rift titles by connecting an Oculus Link cable to a compatible gaming PC. Oculus Link Cable sold separately</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">3D Cinematic Sound - Hear in all directions with built-in speakers that deliver cinematic 3D positional audio</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers a&nbsp;</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">&nbsp;in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 31, 1, 1, 61, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(101, NULL, 'Digital Infrared Thermometer for Adults and Kids', '79', '70', 288, 'product-featured-101.jpg', '<h5 class=\"a-spacing-mini a-color-secondary\" style=\"padding: 0px; margin-top: 0px; margin-right: 0px; margin-left: 0px; font-weight: bold; font-size: 13px; line-height: 19px; font-family: \"Amazon Ember\", Arial, sans-serif; margin-bottom: 6px !important; color: rgb(86, 89, 89) !important;\">Safe and Hygienic</h5><p class=\"a-spacing-base\" style=\"padding: 0px; color: rgb(15, 17, 17); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px; margin-bottom: 14px !important;\">No-Touch measuring reads body temperature within 1.18 inches of the center of the forehead without physical contact.</p><h5 class=\"a-spacing-mini a-color-secondary\" style=\"padding: 0px; margin-top: 0px; margin-right: 0px; margin-left: 0px; font-weight: bold; font-size: 13px; line-height: 19px; font-family: \"Amazon Ember\", Arial, sans-serif; margin-bottom: 6px !important; color: rgb(86, 89, 89) !important;\">Tri-Point Sensors Accuracy</h5><p class=\"a-spacing-base\" style=\"padding: 0px; color: rgb(15, 17, 17); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px; margin-bottom: 14px !important;\">An ultra-sensitive infrared sensor collects more than 100 data points per second while distance and environmental sensors combine to account for other variables; ensuring maximum accuracy every time a temperature is taken.</p><h5 class=\"a-spacing-mini a-color-secondary\" style=\"padding: 0px; margin-top: 0px; margin-right: 0px; margin-left: 0px; font-weight: bold; font-size: 13px; line-height: 19px; font-family: \"Amazon Ember\", Arial, sans-serif; margin-bottom: 6px !important; color: rgb(86, 89, 89) !important;\">Fast, Simple, Clear and Quiet</h5><p class=\"a-spacing-base\" style=\"padding: 0px; color: rgb(15, 17, 17); font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px; margin-bottom: 14px !important;\">The intuitive single-button control design thermometer reads the temperature in just 1 second from a clear bright large LED screen, even in total darkness. The quiet vibrating alert eliminates buzzing noise or disturbance.</p>', '<p style=\"padding: 0px; margin-top: 0px; text-rendering: optimizelegibility; margin-bottom: 0px !important; line-height: 32px !important;\"><span id=\"productTitle\" class=\"a-size-large product-title-word-break\" style=\"text-rendering: optimizelegibility; word-break: break-word; line-height: 32px !important;\">No-Touch Forehead Thermometer</span></p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: \"Amazon Ember\", Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">No Touch Measuring, Just Safe and Hygienic: PT3 Built-in infrared temperature sensor, reads body temperature within 1.18 inches of the center of the forehead without physical contact.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Tri-Point Sensors Accuracy: An ultra-sensitive infrared sensor collects more than 100 data points per second while distance and environmental sensors combine to account for other variables; ensuring maximum accuracy every time temperature is taken.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Fast, Simple, Clear and Quiet: The intuitive single-button control design thermometer reads the temperature in just 1 second from a clear bright extra-large LED screen, even in total darkness. The quiet vibration alerting ensures there is no buzzing noise and no disturbance.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">Suitable for Multi-Scenario and All Ages: iHealth PT3 is designed for all ages: ranging from babies and toddlers to the elderly. An ideal choice for hospitals, hotels, school settings, and public establishments.</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\">What You Get: 1x PT3 thermometer, 2x AAA batteries, 1x Instruction manual, 1x Quick User Guide, our worry-free 12-month warranty, and friendly California-based customer service.</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers aÂ </span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Â in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 23, 1, 1, 73, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
(102, NULL, 'Women\'s Plus-Size Shirt Dress with Gold Hardware', '190', '169', 80, 'product-featured-102.jpg', '<p><span style=\"color: rgb(15, 17, 17); font-family: &quot;Amazon Ember&quot;, Arial, sans-serif; font-size: 14px;\">This dress has everything! Enough stretch to be forgiving. Works for my hourglass/pear shape just fine. The cap sleeves satisfy conservative dressing requirements (no sleeveless) but still works for spring/summer.</span><br></p>', '<p>From Calvin Klein</p>', '<ul class=\"a-unordered-list a-vertical a-spacing-mini\" style=\"margin-right: 0px; margin-bottom: 0px; margin-left: 18px; color: rgb(15, 17, 17); padding: 0px; font-family: &quot;Amazon Ember&quot;, Arial, sans-serif; font-size: 14px;\"><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Cap-sleeve shirt dress featuring belted waist with gold-tone hardware accents</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Split v-neckline</span></li><li style=\"list-style: disc; overflow-wrap: break-word; margin: 0px;\"><span class=\"a-list-item\" style=\"overflow-wrap: break-word; display: block;\">Exposed center back zipper</span></li></ul>', '<p><span style=\"color: rgb(51, 51, 51); font-size: 14px;\">This is a sample text for conditions.</span><br></p>', '<p><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">Offers a&nbsp;</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">15 to 30-day window</span><span style=\"margin: 0px; padding: 0px; color: rgb(32, 33, 36); font-family: arial, sans-serif; font-size: 16px;\">&nbsp;in which customers can return a product and ask for a refund. Some businesses extend that period up to 90 days. Regardless of the time frame you choose, ensuring that you actually have a time frame is essential.</span><br></p>', 68, 1, 1, 32, '', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_product_color`
--

CREATE TABLE `tbl_product_color` (
  `id` int(11) NOT NULL,
  `color_id` int(11) NOT NULL,
  `p_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_product_color`
--

INSERT INTO `tbl_product_color` (`id`, `color_id`, `p_id`) VALUES
(69, 1, 4),
(70, 4, 4),
(77, 6, 6),
(82, 2, 12),
(83, 9, 13),
(84, 3, 14),
(85, 2, 15),
(86, 6, 15),
(87, 3, 16),
(88, 3, 17),
(89, 2, 18),
(90, 3, 19),
(91, 1, 20),
(92, 8, 21),
(93, 2, 22),
(94, 2, 23),
(95, 2, 25),
(96, 5, 26),
(97, 2, 27),
(98, 4, 27),
(99, 5, 28),
(100, 7, 29),
(101, 10, 30),
(102, 11, 31),
(103, 14, 32),
(105, 2, 34),
(106, 1, 35),
(107, 3, 36),
(109, 6, 38),
(110, 2, 39),
(111, 11, 42),
(149, 3, 10),
(150, 6, 9),
(151, 3, 8),
(152, 7, 7),
(159, 2, 77),
(163, 17, 79),
(164, 2, 78),
(167, 3, 80),
(168, 2, 81),
(172, 1, 82),
(173, 2, 82),
(174, 4, 82),
(195, 2, 84),
(201, 2, 86),
(202, 6, 86),
(203, 17, 86),
(222, 16, 93),
(223, 21, 85),
(224, 22, 85),
(225, 23, 85),
(226, 1, 83),
(227, 2, 83),
(228, 3, 83),
(229, 4, 83),
(230, 5, 83),
(231, 6, 83),
(232, 8, 83),
(233, 14, 83),
(234, 17, 83),
(235, 18, 83),
(236, 12, 89),
(237, 27, 91),
(239, 2, 92),
(240, 29, 92),
(241, 2, 88),
(242, 8, 88),
(243, 17, 88),
(244, 2, 90),
(245, 6, 90),
(246, 25, 90),
(247, 27, 90),
(248, 28, 90),
(251, 2, 95),
(252, 6, 95),
(256, 2, 94),
(257, 3, 87),
(258, 17, 87),
(261, 25, 97),
(262, 5, 98),
(263, 6, 99),
(266, 6, 101),
(267, 2, 102);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_product_photo`
--

CREATE TABLE `tbl_product_photo` (
  `pp_id` int(11) NOT NULL,
  `photo` varchar(255) NOT NULL,
  `p_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_product_photo`
--

INSERT INTO `tbl_product_photo` (`pp_id`, `photo`, `p_id`) VALUES
(106, '106.jpg', 83),
(107, '107.jpg', 83),
(108, '108.jpg', 84),
(109, '109.jpg', 84),
(110, '110.jpg', 85),
(111, '111.jpg', 85),
(112, '112.jpg', 86),
(113, '113.jpg', 86),
(114, '114.jpg', 87),
(115, '115.jpg', 87),
(116, '116.jpg', 88),
(117, '117.jpg', 88),
(118, '118.jpg', 89),
(119, '119.jpg', 89),
(120, '120.jpg', 90),
(121, '121.jpg', 91),
(122, '122.jpg', 92),
(123, '123.jpg', 92),
(124, '124.jpg', 93),
(125, '125.jpg', 94),
(126, '126.jpg', 95),
(128, '128.jpg', 97),
(129, '129.jpg', 98),
(130, '130.jpg', 98),
(132, '132.jpg', 102);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_product_size`
--

CREATE TABLE `tbl_product_size` (
  `id` int(11) NOT NULL,
  `size_id` int(11) NOT NULL,
  `p_id` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_product_size`
--

INSERT INTO `tbl_product_size` (`id`, `size_id`, `p_id`) VALUES
(44, 1, 6),
(56, 8, 12),
(57, 9, 12),
(58, 10, 12),
(59, 11, 12),
(60, 12, 12),
(61, 13, 12),
(62, 9, 13),
(63, 11, 13),
(64, 13, 13),
(65, 15, 13),
(66, 9, 14),
(67, 11, 14),
(68, 12, 14),
(69, 13, 14),
(70, 9, 15),
(71, 11, 15),
(72, 13, 15),
(73, 15, 16),
(74, 16, 16),
(75, 17, 16),
(76, 16, 17),
(77, 17, 17),
(78, 14, 18),
(79, 15, 18),
(80, 16, 18),
(81, 17, 18),
(82, 15, 19),
(83, 16, 19),
(84, 17, 19),
(85, 14, 20),
(86, 15, 20),
(87, 17, 20),
(88, 15, 21),
(89, 17, 21),
(90, 15, 22),
(91, 16, 22),
(92, 17, 22),
(93, 15, 23),
(94, 16, 23),
(95, 17, 23),
(96, 18, 25),
(97, 19, 25),
(98, 20, 25),
(99, 21, 25),
(100, 19, 26),
(101, 21, 26),
(102, 22, 26),
(103, 23, 26),
(104, 19, 27),
(105, 20, 27),
(106, 21, 27),
(107, 22, 27),
(108, 19, 28),
(109, 20, 28),
(110, 21, 28),
(111, 19, 29),
(112, 20, 29),
(113, 22, 29),
(114, 1, 30),
(115, 2, 30),
(116, 3, 30),
(117, 4, 30),
(118, 23, 31),
(119, 26, 32),
(123, 2, 34),
(124, 2, 35),
(125, 2, 36),
(126, 3, 36),
(129, 2, 38),
(130, 3, 38),
(131, 4, 38),
(132, 5, 38),
(133, 27, 39),
(134, 8, 42),
(210, 3, 10),
(211, 4, 10),
(212, 5, 10),
(213, 6, 10),
(214, 3, 9),
(215, 4, 9),
(216, 3, 8),
(217, 4, 8),
(218, 2, 7),
(219, 3, 7),
(220, 4, 7),
(249, 1, 79),
(250, 2, 79),
(251, 3, 79),
(252, 1, 78),
(253, 2, 78),
(254, 3, 78),
(255, 4, 78),
(256, 5, 78),
(259, 26, 80),
(262, 3, 82),
(263, 4, 82),
(278, 2, 84),
(279, 3, 84),
(280, 4, 84),
(281, 5, 84),
(282, 6, 84),
(305, 26, 86),
(339, 27, 93),
(340, 15, 85),
(341, 16, 85),
(342, 17, 85),
(343, 18, 85),
(344, 19, 85),
(345, 20, 85),
(346, 21, 85),
(347, 22, 85),
(348, 23, 85),
(349, 24, 85),
(350, 25, 85),
(351, 1, 83),
(352, 2, 83),
(353, 3, 83),
(354, 4, 83),
(355, 5, 83),
(356, 6, 83),
(357, 7, 83),
(358, 3, 89),
(359, 4, 89),
(360, 5, 89),
(361, 6, 89),
(362, 7, 89),
(363, 2, 91),
(364, 3, 91),
(365, 4, 91),
(366, 5, 91),
(367, 6, 91),
(369, 27, 92),
(370, 3, 88),
(371, 4, 88),
(372, 5, 88),
(373, 6, 88),
(374, 7, 88),
(375, 1, 90),
(376, 2, 90),
(377, 3, 90),
(378, 4, 90),
(380, 27, 95),
(398, 33, 94),
(399, 29, 87),
(400, 30, 87),
(401, 31, 87),
(402, 32, 87),
(403, 33, 87),
(404, 34, 87),
(405, 35, 87),
(406, 36, 87),
(407, 37, 87),
(408, 38, 87),
(409, 39, 87),
(418, 8, 97),
(419, 9, 97),
(420, 10, 97),
(421, 11, 97),
(422, 12, 97),
(423, 13, 97),
(424, 14, 97),
(425, 15, 97),
(426, 16, 97),
(427, 17, 97),
(428, 18, 97),
(429, 19, 97),
(430, 4, 98),
(431, 5, 98),
(432, 6, 98),
(433, 7, 98),
(434, 40, 99),
(435, 41, 99),
(441, 27, 101),
(442, 42, 102),
(443, 43, 102),
(444, 44, 102),
(445, 45, 102),
(446, 46, 102),
(447, 47, 102);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_product_variants`
--

CREATE TABLE `tbl_product_variants` (
  `variant_id` int(11) NOT NULL,
  `p_id` int(11) NOT NULL,
  `size_id` int(11) NOT NULL,
  `color_id` int(11) NOT NULL,
  `variant_price` decimal(10,2) NOT NULL,
  `variant_qty` int(11) DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_professionals`
--

CREATE TABLE `tbl_professionals` (
  `professional_id` int(11) NOT NULL,
  `user_id` int(11) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `phone` varchar(50) NOT NULL,
  `nid_number` varchar(50) DEFAULT NULL,
  `nid_photo_front` varchar(255) DEFAULT NULL,
  `nid_photo_back` varchar(255) DEFAULT NULL,
  `license_photo` varchar(255) DEFAULT NULL,
  `additional_docs` text DEFAULT NULL,
  `category_id` int(11) DEFAULT NULL,
  `bio` text DEFAULT NULL,
  `address` varchar(255) DEFAULT NULL,
  `city` varchar(100) DEFAULT NULL,
  `state` varchar(100) DEFAULT NULL,
  `zip` varchar(20) DEFAULT NULL,
  `latitude` decimal(10,8) DEFAULT NULL,
  `longitude` decimal(11,8) DEFAULT NULL,
  `verification_status` enum('pending','approved','rejected') NOT NULL DEFAULT 'pending',
  `documents_uploaded` tinyint(1) NOT NULL DEFAULT 0,
  `profile_photo` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_professional_categories`
--

CREATE TABLE `tbl_professional_categories` (
  `category_id` int(11) NOT NULL,
  `category_name_en` varchar(255) NOT NULL,
  `category_name_bn` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_professional_reviews`
--

CREATE TABLE `tbl_professional_reviews` (
  `review_id` int(11) NOT NULL,
  `professional_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `rating` tinyint(1) NOT NULL CHECK (`rating` >= 1 and `rating` <= 5),
  `review_text` text DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_professional_services`
--

CREATE TABLE `tbl_professional_services` (
  `service_id` int(11) NOT NULL,
  `professional_id` int(11) NOT NULL,
  `service_name` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `duration_minutes` int(11) DEFAULT NULL,
  `is_available` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_project_votes`
--

CREATE TABLE `tbl_project_votes` (
  `vote_id` int(11) NOT NULL,
  `project_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `vote_type` enum('for','against') NOT NULL,
  `voted_at` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_rating`
--

CREATE TABLE `tbl_rating` (
  `rt_id` int(11) NOT NULL,
  `p_id` int(11) NOT NULL,
  `cust_id` int(11) NOT NULL,
  `comment` text NOT NULL,
  `rating` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_restaurants`
--

CREATE TABLE `tbl_restaurants` (
  `restaurant_id` int(11) NOT NULL,
  `business_owner_id` int(11) DEFAULT NULL,
  `name` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `address` varchar(255) NOT NULL,
  `city` varchar(100) NOT NULL,
  `state` varchar(100) DEFAULT NULL,
  `zip` varchar(20) DEFAULT NULL,
  `phone` varchar(50) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `latitude` decimal(10,8) NOT NULL,
  `longitude` decimal(11,8) NOT NULL,
  `opening_time` time DEFAULT NULL,
  `closing_time` time DEFAULT NULL,
  `is_open` tinyint(1) NOT NULL DEFAULT 1,
  `status` enum('pending','approved','rejected') NOT NULL DEFAULT 'pending',
  `logo` varchar(255) DEFAULT NULL,
  `cover_photo` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_review`
--

CREATE TABLE `tbl_review` (
  `review_id` int(11) NOT NULL,
  `cust_id` int(11) NOT NULL,
  `product_id` int(11) NOT NULL,
  `rating` int(11) NOT NULL,
  `review_text` text NOT NULL,
  `review_date` datetime NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'Pending',
  `created_at` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_review_image`
--

CREATE TABLE `tbl_review_image` (
  `image_id` int(11) NOT NULL,
  `review_id` int(11) NOT NULL,
  `image_path` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_service`
--

CREATE TABLE `tbl_service` (
  `id` int(11) NOT NULL,
  `title` varchar(255) NOT NULL,
  `content` text NOT NULL,
  `photo` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_service`
--

INSERT INTO `tbl_service` (`id`, `title`, `content`, `photo`) VALUES
(5, 'Easy Returns', 'Return any item before 15 days!', 'service-5.png'),
(6, 'Free Shipping', 'Enjoy free shipping inside US.', 'service-6.png'),
(7, 'Fast Shipping', 'Items are shipped within 24 hours.', 'service-7.png'),
(8, 'Satisfaction Guarantee', 'We guarantee you with our quality satisfaction.', 'service-8.png'),
(9, 'Secure Checkout', 'Providing Secure Checkout Options for all', 'service-9.png'),
(10, 'Money Back Guarantee', 'Offer money back guarantee on our products', 'service-10.png');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_settings`
--

CREATE TABLE `tbl_settings` (
  `id` int(11) NOT NULL,
  `logo` text NOT NULL,
  `favicon` text NOT NULL,
  `footer_about` text NOT NULL,
  `footer_copyright` text NOT NULL,
  `contact_address` text NOT NULL,
  `contact_email` text NOT NULL,
  `contact_phone` text NOT NULL,
  `contact_fax` text NOT NULL,
  `contact_map_iframe` text NOT NULL,
  `receive_email` text NOT NULL,
  `receive_email_subject` text NOT NULL,
  `receive_email_thank_you_message` text NOT NULL,
  `forget_password_message` text NOT NULL,
  `total_recent_post_footer` int(10) NOT NULL,
  `total_popular_post_footer` int(10) NOT NULL,
  `total_recent_post_sidebar` int(11) NOT NULL,
  `total_popular_post_sidebar` int(11) NOT NULL,
  `total_featured_product_home` int(11) NOT NULL,
  `total_latest_product_home` int(11) NOT NULL,
  `total_popular_product_home` int(11) NOT NULL,
  `meta_title_home` text NOT NULL,
  `meta_keyword_home` text NOT NULL,
  `meta_description_home` text NOT NULL,
  `google_client_id` varchar(255) DEFAULT NULL,
  `facebook_app_id` varchar(255) DEFAULT NULL,
  `twilio_account_sid` varchar(255) DEFAULT NULL,
  `twilio_auth_token` varchar(255) DEFAULT NULL,
  `twilio_phone_number` varchar(255) DEFAULT NULL,
  `smtp_host` varchar(255) DEFAULT NULL,
  `smtp_username` varchar(255) DEFAULT NULL,
  `smtp_password` varchar(255) DEFAULT NULL,
  `smtp_encryption` varchar(50) DEFAULT 'NONE',
  `smtp_port` int(11) DEFAULT 587,
  `smtp_from_email` varchar(255) DEFAULT 'no-reply@yourdomain.com',
  `smtp_from_name` varchar(255) DEFAULT 'Your Website Name',
  `banner_login` text NOT NULL,
  `banner_registration` text NOT NULL,
  `banner_forget_password` text NOT NULL,
  `banner_reset_password` text NOT NULL,
  `banner_search` text NOT NULL,
  `banner_cart` text NOT NULL,
  `banner_checkout` text NOT NULL,
  `banner_product_category` text NOT NULL,
  `banner_blog` text NOT NULL,
  `cta_title` text NOT NULL,
  `cta_content` text NOT NULL,
  `cta_read_more_text` text NOT NULL,
  `cta_read_more_url` text NOT NULL,
  `cta_photo` text NOT NULL,
  `featured_product_title` text NOT NULL,
  `featured_product_subtitle` text NOT NULL,
  `latest_product_title` text NOT NULL,
  `latest_product_subtitle` text NOT NULL,
  `popular_product_title` text NOT NULL,
  `popular_product_subtitle` text NOT NULL,
  `testimonial_title` text NOT NULL,
  `testimonial_subtitle` text NOT NULL,
  `testimonial_photo` text NOT NULL,
  `blog_title` text NOT NULL,
  `blog_subtitle` text NOT NULL,
  `newsletter_text` text NOT NULL,
  `paypal_email` text NOT NULL,
  `stripe_public_key` text NOT NULL,
  `stripe_secret_key` text NOT NULL,
  `bank_detail` text NOT NULL,
  `before_head` text NOT NULL,
  `after_body` text NOT NULL,
  `before_body` text NOT NULL,
  `home_service_on_off` int(11) NOT NULL,
  `home_welcome_on_off` int(11) NOT NULL,
  `home_featured_product_on_off` int(11) NOT NULL,
  `home_latest_product_on_off` int(11) NOT NULL,
  `home_popular_product_on_off` int(11) NOT NULL,
  `home_testimonial_on_off` int(11) NOT NULL,
  `home_blog_on_off` int(11) NOT NULL,
  `newsletter_on_off` int(11) NOT NULL,
  `ads_above_welcome_on_off` int(1) NOT NULL,
  `ads_above_featured_product_on_off` int(1) NOT NULL,
  `ads_above_latest_product_on_off` int(1) NOT NULL,
  `ads_above_popular_product_on_off` int(1) NOT NULL,
  `ads_above_testimonial_on_off` int(1) NOT NULL,
  `ads_category_sidebar_on_off` int(1) NOT NULL,
  `sslcz_store_id` varchar(255) NOT NULL DEFAULT '',
  `sslcz_store_pass` varchar(255) NOT NULL DEFAULT '',
  `sslcz_mode` enum('sandbox','live') NOT NULL DEFAULT 'sandbox',
  `payment_methods` varchar(255) NOT NULL DEFAULT 'PayPal,Bank Deposit,Cash on Delivery,SSLCommerz',
  `review_feature_on_off` tinyint(1) DEFAULT 1,
  `estimated_delivery_time_local` varchar(255) DEFAULT '3-5 business days',
  `estimated_delivery_time_international` varchar(255) DEFAULT '10-20 business days',
  `gemini_api_key` varchar(255) DEFAULT '',
  `flash_sale_end_time` datetime DEFAULT NULL,
  `free_delivery_threshold_qty` int(11) DEFAULT 5,
  `product_voucher_code` varchar(50) DEFAULT 'SAVE10',
  `product_voucher_discount` decimal(10,2) DEFAULT 10.00,
  `email_method` varchar(50) NOT NULL DEFAULT 'smtp',
  `BASE_URL` varchar(50) NOT NULL DEFAULT 'smtp',
  `paypal_client_id` varchar(50) NOT NULL,
  `paypal_secret` text NOT NULL,
  `paypal_sandbox_mode` text NOT NULL DEFAULT 'Test',
  `cod_enabled` tinyint(1) NOT NULL DEFAULT 0,
  `facebook_app_secret` varchar(255) DEFAULT NULL,
  `google_client_secret` varchar(255) DEFAULT NULL,
  `home_slider_on_off` int(1) DEFAULT 1,
  `home_features_on_off` int(1) DEFAULT 1,
  `home_map_on_off` int(1) NOT NULL DEFAULT 1,
  `home_newsletter_on_off` int(1) NOT NULL DEFAULT 1,
  `home_brand_on_off` int(1) NOT NULL DEFAULT 1,
  `home_category_on_off` int(1) NOT NULL DEFAULT 1,
  `home_slider_order` int(11) NOT NULL DEFAULT 1,
  `home_features_order` int(11) NOT NULL DEFAULT 2,
  `home_category_order` int(11) NOT NULL DEFAULT 3,
  `home_flash_order` int(11) NOT NULL DEFAULT 4,
  `home_featured_product_order` int(11) NOT NULL DEFAULT 5,
  `home_latest_product_order` int(11) NOT NULL DEFAULT 6,
  `home_popular_product_order` int(11) NOT NULL DEFAULT 7,
  `bg_color_categories` varchar(20) DEFAULT '#ffffff',
  `bg_color_latest_products` varchar(20) DEFAULT '#ffffff',
  `show_scroll_top_btn` tinyint(1) DEFAULT 0,
  `slider_side_banner_img` varchar(255) DEFAULT 'side-banner.jpg',
  `slider_side_banner_text` text DEFAULT NULL,
  `extra_footer_section_enable` tinyint(1) DEFAULT 1,
  `home_sticky_nav_on_off` tinyint(1) NOT NULL DEFAULT 1,
  `home_sticky_nav_order` int(11) NOT NULL DEFAULT 8,
  `multi_vendor_active` tinyint(1) DEFAULT 1,
  `coin_system_active` tinyint(1) DEFAULT 1,
  `sticky_header_mobile` tinyint(1) DEFAULT 1,
  `sticky_header_desktop` tinyint(1) DEFAULT 1,
  `multi_vendor_on_off` tinyint(1) DEFAULT 0,
  `coin_payment_system_on_off` tinyint(1) DEFAULT 0,
  `chat_system_on_off` tinyint(1) DEFAULT 1,
  `coin_payment_on_off` tinyint(1) DEFAULT 0,
  `desktop_advanced_layout_on_off` tinyint(1) DEFAULT 1,
  `hide_banner_desktop` tinyint(1) NOT NULL DEFAULT 0,
  `hide_banner_mobile` tinyint(1) NOT NULL DEFAULT 0,
  `hide_free_delivery_desktop` tinyint(1) NOT NULL DEFAULT 0,
  `hide_free_delivery_mobile` tinyint(1) NOT NULL DEFAULT 0,
  `bg_color_featured_products` varchar(255) NOT NULL DEFAULT '#ffffff',
  `featured_product_count` int(11) NOT NULL DEFAULT 8
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci ROW_FORMAT=DYNAMIC;

--
-- Dumping data for table `tbl_settings`
--

INSERT INTO `tbl_settings` (`id`, `logo`, `favicon`, `footer_about`, `footer_copyright`, `contact_address`, `contact_email`, `contact_phone`, `contact_fax`, `contact_map_iframe`, `receive_email`, `receive_email_subject`, `receive_email_thank_you_message`, `forget_password_message`, `total_recent_post_footer`, `total_popular_post_footer`, `total_recent_post_sidebar`, `total_popular_post_sidebar`, `total_featured_product_home`, `total_latest_product_home`, `total_popular_product_home`, `meta_title_home`, `meta_keyword_home`, `meta_description_home`, `google_client_id`, `facebook_app_id`, `twilio_account_sid`, `twilio_auth_token`, `twilio_phone_number`, `smtp_host`, `smtp_username`, `smtp_password`, `smtp_encryption`, `smtp_port`, `smtp_from_email`, `smtp_from_name`, `banner_login`, `banner_registration`, `banner_forget_password`, `banner_reset_password`, `banner_search`, `banner_cart`, `banner_checkout`, `banner_product_category`, `banner_blog`, `cta_title`, `cta_content`, `cta_read_more_text`, `cta_read_more_url`, `cta_photo`, `featured_product_title`, `featured_product_subtitle`, `latest_product_title`, `latest_product_subtitle`, `popular_product_title`, `popular_product_subtitle`, `testimonial_title`, `testimonial_subtitle`, `testimonial_photo`, `blog_title`, `blog_subtitle`, `newsletter_text`, `paypal_email`, `stripe_public_key`, `stripe_secret_key`, `bank_detail`, `before_head`, `after_body`, `before_body`, `home_service_on_off`, `home_welcome_on_off`, `home_featured_product_on_off`, `home_latest_product_on_off`, `home_popular_product_on_off`, `home_testimonial_on_off`, `home_blog_on_off`, `newsletter_on_off`, `ads_above_welcome_on_off`, `ads_above_featured_product_on_off`, `ads_above_latest_product_on_off`, `ads_above_popular_product_on_off`, `ads_above_testimonial_on_off`, `ads_category_sidebar_on_off`, `sslcz_store_id`, `sslcz_store_pass`, `sslcz_mode`, `payment_methods`, `review_feature_on_off`, `estimated_delivery_time_local`, `estimated_delivery_time_international`, `gemini_api_key`, `flash_sale_end_time`, `free_delivery_threshold_qty`, `product_voucher_code`, `product_voucher_discount`, `email_method`, `BASE_URL`, `paypal_client_id`, `paypal_secret`, `paypal_sandbox_mode`, `cod_enabled`, `facebook_app_secret`, `google_client_secret`, `home_slider_on_off`, `home_features_on_off`, `home_map_on_off`, `home_newsletter_on_off`, `home_brand_on_off`, `home_category_on_off`, `home_slider_order`, `home_features_order`, `home_category_order`, `home_flash_order`, `home_featured_product_order`, `home_latest_product_order`, `home_popular_product_order`, `bg_color_categories`, `bg_color_latest_products`, `show_scroll_top_btn`, `slider_side_banner_img`, `slider_side_banner_text`, `extra_footer_section_enable`, `home_sticky_nav_on_off`, `home_sticky_nav_order`, `multi_vendor_active`, `coin_system_active`, `sticky_header_mobile`, `sticky_header_desktop`, `multi_vendor_on_off`, `coin_payment_system_on_off`, `chat_system_on_off`, `coin_payment_on_off`, `desktop_advanced_layout_on_off`, `hide_banner_desktop`, `hide_banner_mobile`, `hide_free_delivery_desktop`, `hide_free_delivery_mobile`, `bg_color_featured_products`, `featured_product_count`) VALUES
(1, 'logo-1768060821-4cfa5d2ae5.png', 'favicon-1768060928-ae939e44e1.png', 'About Us text here.', 'Copyright © 2025 All Rights Reserved.', '123 Shopping Street, Your City', 'contact@yourwebsite.com', '01863054816', '', '<p>Map Iframe Code Here</p>', 'studentroutinemanager@gmail.com', 'New Contact Form Message', 'Thank you for contacting us!', 'Password reset instructions here.', 0, 0, 0, 0, 0, 0, 0, 'Home Page', 'ecommerce, shop, products', 'This is the description of the home page.', '', '', '', '', '', 'smtp.gmail.com', '', '', 'SSL', 465, '', 'JoyStore', 'banner_login.jpg', 'banner_registration.jpg', 'banner_forget_password.jpg', 'banner_reset_password.jpg', 'banner_search.jpg', 'banner_cart.jpg', 'banner_checkout.jpg', 'banner_product_category.jpg', '', '', '', '', '', '', 'Featured Products', 'Check out our best products', 'Latest Products', 'See what is new in our store', 'Popular Products', 'Products that everyone loves', '', '', '', '', '', '', 'paypal-business@yourwebsite.com', '', '', 'Your Bank Details Here...', '', '', '', 0, 0, 1, 1, 1, 0, 0, 1, 0, 0, 0, 0, 0, 0, 'pract663184d9bae59', 'pract663184d9bae59@ssl', 'sandbox', 'Bank Deposit,Cash on Delivery,SSLCommerz', 1, '3-5 business days', '10-20 business days', '', NULL, 5, 'SAVE10', 10.00, 'PHP Mail', '', '', '', '0', 1, '', '', 1, 1, 0, 0, 0, 1, 5, 1, 2, 4, 3, 6, 7, '#fb9dab', '#dab2f0', 1, 'side-banner.jpg', '', 1, 1, 8, 1, 1, 1, 1, 0, 0, 1, 0, 1, 1, 1, 1, 1, '#ffffff', 8);

-- --------------------------------------------------------

--
-- Table structure for table `tbl_shipping_cost`
--

CREATE TABLE `tbl_shipping_cost` (
  `shipping_cost_id` int(11) NOT NULL,
  `country_id` int(11) NOT NULL,
  `amount` varchar(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_shipping_cost`
--

INSERT INTO `tbl_shipping_cost` (`shipping_cost_id`, `country_id`, `amount`) VALUES
(1, 228, '11'),
(2, 167, '10'),
(3, 13, '8'),
(4, 230, '0');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_shipping_cost_all`
--

CREATE TABLE `tbl_shipping_cost_all` (
  `sca_id` int(11) NOT NULL,
  `amount` varchar(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_shipping_cost_all`
--

INSERT INTO `tbl_shipping_cost_all` (`sca_id`, `amount`) VALUES
(1, '100');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_size`
--

CREATE TABLE `tbl_size` (
  `size_id` int(11) NOT NULL,
  `size_name` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

--
-- Dumping data for table `tbl_size`
--

INSERT INTO `tbl_size` (`size_id`, `size_name`) VALUES
(1, 'XS'),
(2, 'S'),
(3, 'M'),
(4, 'L'),
(5, 'XL'),
(6, 'XXL'),
(7, '3XL'),
(8, '31'),
(9, '32'),
(10, '33'),
(11, '34'),
(12, '35'),
(13, '36'),
(14, '37'),
(15, '38'),
(16, '39'),
(17, '40'),
(18, '41'),
(19, '42'),
(20, '43'),
(21, '44'),
(22, '45'),
(23, '46'),
(24, '47'),
(25, '48'),
(26, 'Free Size'),
(27, 'One Size for All'),
(28, '10'),
(29, '12 Months'),
(30, '2T'),
(31, '3T'),
(32, '4T'),
(33, '5T'),
(34, '6 Years'),
(35, '7 Years'),
(36, '8 Years'),
(37, '10 Years'),
(38, '12 Years'),
(39, '14 Years'),
(40, '256 GB'),
(41, '128 GB'),
(42, '14 Plus'),
(43, '16 Plus'),
(44, '18 Plus'),
(45, '20 Plus'),
(46, '22 Plus'),
(47, '24 Plus');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_slider`
--

CREATE TABLE `tbl_slider` (
  `id` int(11) NOT NULL,
  `photo` varchar(255) NOT NULL,
  `heading` varchar(255) DEFAULT NULL,
  `content` text DEFAULT NULL,
  `button_text` varchar(255) DEFAULT NULL,
  `button_url` varchar(255) DEFAULT NULL,
  `position` varchar(50) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_social`
--

CREATE TABLE `tbl_social` (
  `id` int(11) NOT NULL,
  `social_name` varchar(100) NOT NULL,
  `social_url` varchar(255) DEFAULT ''
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_social`
--

INSERT INTO `tbl_social` (`id`, `social_name`, `social_url`) VALUES
(1, 'Facebook', ''),
(2, 'Twitter', ''),
(3, 'LinkedIn', ''),
(4, 'Google Plus', ''),
(5, 'Pinterest', ''),
(6, 'YouTube', ''),
(7, 'Instagram', ''),
(8, 'Tumblr', ''),
(9, 'Flickr', ''),
(10, 'Reddit', ''),
(11, 'Snapchat', ''),
(12, 'WhatsApp', ''),
(13, 'Quora', ''),
(14, 'StumbleUpon', ''),
(15, 'Delicious', ''),
(16, 'Digg', '');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_subscriber`
--

CREATE TABLE `tbl_subscriber` (
  `subs_id` int(11) NOT NULL,
  `subs_email` varchar(255) NOT NULL,
  `subs_date` varchar(255) NOT NULL,
  `subs_hash` varchar(255) NOT NULL,
  `subs_active` int(11) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=latin1 COLLATE=latin1_swedish_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_top_category`
--

CREATE TABLE `tbl_top_category` (
  `tcat_id` int(11) NOT NULL,
  `tcat_name` varchar(255) NOT NULL,
  `show_on_menu` tinyint(1) NOT NULL DEFAULT 0,
  `tcat_order` int(11) NOT NULL,
  `photo` varchar(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_top_category`
--

INSERT INTO `tbl_top_category` (`tcat_id`, `tcat_name`, `show_on_menu`, `tcat_order`, `photo`) VALUES
(1, 'yiyi', 1, 0, '');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_transport_bookings`
--

CREATE TABLE `tbl_transport_bookings` (
  `booking_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `driver_id` int(11) DEFAULT NULL,
  `vehicle_type` enum('rickshaw','cng','bike','car') NOT NULL,
  `pickup_latitude` decimal(10,8) NOT NULL,
  `pickup_longitude` decimal(11,8) NOT NULL,
  `dropoff_latitude` decimal(10,8) NOT NULL,
  `dropoff_longitude` decimal(11,8) NOT NULL,
  `pickup_address` text NOT NULL,
  `dropoff_address` text NOT NULL,
  `estimated_fare` decimal(10,2) DEFAULT NULL,
  `actual_fare` decimal(10,2) DEFAULT NULL,
  `distance_km` decimal(10,2) DEFAULT NULL,
  `status` enum('pending','accepted','started','completed','cancelled') NOT NULL DEFAULT 'pending',
  `payment_status` enum('unpaid','paid','refunded') NOT NULL DEFAULT 'unpaid',
  `payment_method` varchar(50) DEFAULT NULL,
  `booked_at` datetime DEFAULT current_timestamp(),
  `accepted_at` datetime DEFAULT NULL,
  `started_at` datetime DEFAULT NULL,
  `completed_at` datetime DEFAULT NULL,
  `cancelled_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_travel_agencies`
--

CREATE TABLE `tbl_travel_agencies` (
  `agency_id` int(11) NOT NULL,
  `owner_user_id` int(11) NOT NULL,
  `agency_name` varchar(255) NOT NULL,
  `contact_email` varchar(255) NOT NULL,
  `contact_phone` varchar(50) NOT NULL,
  `address` varchar(255) DEFAULT NULL,
  `description` text DEFAULT NULL,
  `logo` varchar(255) DEFAULT NULL,
  `verification_status` enum('pending','approved','rejected') NOT NULL DEFAULT 'pending',
  `is_active` tinyint(1) NOT NULL DEFAULT 1,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_travel_bookings`
--

CREATE TABLE `tbl_travel_bookings` (
  `booking_id` int(11) NOT NULL,
  `user_id` int(11) NOT NULL,
  `agency_id` int(11) DEFAULT NULL,
  `booking_type` enum('hotel','flight','bus','train','package') NOT NULL,
  `service_provider` varchar(255) DEFAULT NULL,
  `destination` varchar(255) NOT NULL,
  `check_in_date` date DEFAULT NULL,
  `check_out_date` date DEFAULT NULL,
  `departure_date` date DEFAULT NULL,
  `return_date` date DEFAULT NULL,
  `total_passengers` int(11) NOT NULL DEFAULT 1,
  `total_amount` decimal(10,2) NOT NULL,
  `payment_status` enum('pending','paid','refunded') NOT NULL DEFAULT 'pending',
  `booking_status` enum('pending','confirmed','cancelled','completed') NOT NULL DEFAULT 'pending',
  `booking_details_json` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_bin DEFAULT NULL CHECK (json_valid(`booking_details_json`)),
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_used_products`
--

CREATE TABLE `tbl_used_products` (
  `used_product_id` int(11) NOT NULL,
  `seller_user_id` int(11) NOT NULL,
  `product_name` varchar(255) NOT NULL,
  `description` text DEFAULT NULL,
  `price` decimal(10,2) NOT NULL,
  `condition` enum('new_like','good','fair','used') NOT NULL DEFAULT 'good',
  `category_id` int(11) DEFAULT NULL,
  `location_address` varchar(255) DEFAULT NULL,
  `latitude` decimal(10,8) DEFAULT NULL,
  `longitude` decimal(11,8) DEFAULT NULL,
  `main_photo` varchar(255) DEFAULT NULL,
  `other_photos` text DEFAULT NULL,
  `status` enum('active','sold','pending_approval','rejected') NOT NULL DEFAULT 'pending_approval',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_user`
--

CREATE TABLE `tbl_user` (
  `id` int(11) NOT NULL,
  `full_name` varchar(255) NOT NULL,
  `email` varchar(255) NOT NULL,
  `phone` varchar(50) NOT NULL DEFAULT '',
  `photo` varchar(255) NOT NULL DEFAULT 'default.png',
  `role` varchar(50) NOT NULL DEFAULT 'User',
  `password` varchar(255) NOT NULL,
  `status` varchar(50) NOT NULL DEFAULT 'Active'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_user`
--

INSERT INTO `tbl_user` (`id`, `full_name`, `email`, `phone`, `photo`, `role`, `password`, `status`) VALUES
(2, 'Admin User', 'admin@example.com', '', 'default.png', 'User', '0192023a7bbd73250516f069df18b500', 'Active');

-- --------------------------------------------------------

--
-- Table structure for table `tbl_vehicles`
--

CREATE TABLE `tbl_vehicles` (
  `vehicle_id` int(11) NOT NULL,
  `driver_id` int(11) NOT NULL,
  `make` varchar(100) DEFAULT NULL,
  `model` varchar(100) DEFAULT NULL,
  `license_plate` varchar(50) NOT NULL,
  `color` varchar(50) DEFAULT NULL,
  `photo` varchar(255) DEFAULT NULL,
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_vouchers`
--

CREATE TABLE `tbl_vouchers` (
  `voucher_id` int(11) NOT NULL,
  `business_id` int(11) DEFAULT NULL,
  `shop_id` int(11) DEFAULT NULL,
  `voucher_code` varchar(50) NOT NULL,
  `voucher_name` varchar(255) NOT NULL,
  `voucher_value` decimal(10,2) NOT NULL,
  `min_purchase_amount` decimal(10,2) NOT NULL DEFAULT 0.00,
  `usage_limit` int(11) NOT NULL DEFAULT 1,
  `used_count` int(11) NOT NULL DEFAULT 0,
  `issue_date` datetime NOT NULL,
  `expiry_date` datetime NOT NULL,
  `status` enum('active','inactive','expired') NOT NULL DEFAULT 'active',
  `created_at` datetime DEFAULT current_timestamp(),
  `updated_at` datetime DEFAULT current_timestamp() ON UPDATE current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tbl_wishlist`
--

CREATE TABLE `tbl_wishlist` (
  `wishlist_id` int(11) NOT NULL,
  `cust_id` int(11) NOT NULL COMMENT 'References tbl_customer.cust_id',
  `product_id` int(11) NOT NULL COMMENT 'References tbl_product.p_id',
  `added_date` datetime NOT NULL DEFAULT current_timestamp() COMMENT 'Timestamp of when the product was added'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `tbl_wishlist`
--

INSERT INTO `tbl_wishlist` (`wishlist_id`, `cust_id`, `product_id`, `added_date`) VALUES
(1, 21, 102, '2025-11-08 00:01:11'),
(2, 21, 84, '2025-11-08 00:06:37'),
(3, 21, 97, '2025-11-08 00:06:40'),
(6, 23, 83, '2026-01-19 14:47:29');

--
-- Indexes for dumped tables
--

--
-- Indexes for table `tbl_advertisements`
--
ALTER TABLE `tbl_advertisements`
  ADD PRIMARY KEY (`ad_id`),
  ADD KEY `fk_ad_advertiser` (`advertiser_user_id`);

--
-- Indexes for table `tbl_auctions`
--
ALTER TABLE `tbl_auctions`
  ADD PRIMARY KEY (`auction_id`),
  ADD KEY `fk_auction_seller` (`seller_user_id`),
  ADD KEY `fk_auction_highest_bidder` (`highest_bidder_user_id`);

--
-- Indexes for table `tbl_auction_bids`
--
ALTER TABLE `tbl_auction_bids`
  ADD PRIMARY KEY (`bid_id`),
  ADD KEY `fk_bid_auction` (`auction_id`),
  ADD KEY `fk_bid_bidder` (`bidder_user_id`);

--
-- Indexes for table `tbl_businesses`
--
ALTER TABLE `tbl_businesses`
  ADD PRIMARY KEY (`business_id`),
  ADD UNIQUE KEY `email` (`email`),
  ADD KEY `fk_business_owner` (`owner_user_id`);

--
-- Indexes for table `tbl_coin_transactions`
--
ALTER TABLE `tbl_coin_transactions`
  ADD PRIMARY KEY (`transaction_id`),
  ADD KEY `fk_coin_transaction_user` (`user_id`);

--
-- Indexes for table `tbl_coupon`
--
ALTER TABLE `tbl_coupon`
  ADD PRIMARY KEY (`coupon_id`),
  ADD UNIQUE KEY `coupon_code` (`coupon_code`);

--
-- Indexes for table `tbl_customer`
--
ALTER TABLE `tbl_customer`
  ADD PRIMARY KEY (`cust_id`);

--
-- Indexes for table `tbl_customer_carts`
--
ALTER TABLE `tbl_customer_carts`
  ADD PRIMARY KEY (`cart_id`),
  ADD KEY `customer_id` (`customer_id`),
  ADD KEY `product_id` (`product_id`);

--
-- Indexes for table `tbl_drivers`
--
ALTER TABLE `tbl_drivers`
  ADD PRIMARY KEY (`driver_id`),
  ADD UNIQUE KEY `phone` (`phone`),
  ADD UNIQUE KEY `license_number` (`license_number`),
  ADD UNIQUE KEY `user_id` (`user_id`),
  ADD UNIQUE KEY `email` (`email`);

--
-- Indexes for table `tbl_emergency_contacts`
--
ALTER TABLE `tbl_emergency_contacts`
  ADD PRIMARY KEY (`contact_id`);

--
-- Indexes for table `tbl_features`
--
ALTER TABLE `tbl_features`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_food_orders`
--
ALTER TABLE `tbl_food_orders`
  ADD PRIMARY KEY (`order_id`),
  ADD KEY `fk_food_order_user` (`user_id`),
  ADD KEY `fk_food_order_restaurant` (`restaurant_id`),
  ADD KEY `fk_food_order_driver` (`driver_id`);

--
-- Indexes for table `tbl_food_order_items`
--
ALTER TABLE `tbl_food_order_items`
  ADD PRIMARY KEY (`order_item_id`),
  ADD KEY `fk_order_item_order` (`order_id`),
  ADD KEY `fk_order_item_item` (`item_id`);

--
-- Indexes for table `tbl_gov_projects`
--
ALTER TABLE `tbl_gov_projects`
  ADD PRIMARY KEY (`project_id`);

--
-- Indexes for table `tbl_home_sections`
--
ALTER TABLE `tbl_home_sections`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_home_tabs`
--
ALTER TABLE `tbl_home_tabs`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_hotels`
--
ALTER TABLE `tbl_hotels`
  ADD PRIMARY KEY (`hotel_id`),
  ADD KEY `fk_hotel_owner` (`owner_user_id`),
  ADD KEY `fk_hotel_agency` (`travel_agency_id`);

--
-- Indexes for table `tbl_hotel_rooms`
--
ALTER TABLE `tbl_hotel_rooms`
  ADD PRIMARY KEY (`room_id`),
  ADD KEY `fk_room_hotel` (`hotel_id`);

--
-- Indexes for table `tbl_house_rentals`
--
ALTER TABLE `tbl_house_rentals`
  ADD PRIMARY KEY (`rental_id`),
  ADD KEY `fk_rental_owner` (`owner_user_id`);

--
-- Indexes for table `tbl_house_sales`
--
ALTER TABLE `tbl_house_sales`
  ADD PRIMARY KEY (`sale_id`),
  ADD KEY `fk_sale_owner` (`owner_user_id`);

--
-- Indexes for table `tbl_kyc_verifications`
--
ALTER TABLE `tbl_kyc_verifications`
  ADD PRIMARY KEY (`kyc_id`),
  ADD KEY `cust_id` (`cust_id`);

--
-- Indexes for table `tbl_menu_categories`
--
ALTER TABLE `tbl_menu_categories`
  ADD PRIMARY KEY (`menu_category_id`),
  ADD KEY `fk_menu_category_restaurant` (`restaurant_id`);

--
-- Indexes for table `tbl_menu_items`
--
ALTER TABLE `tbl_menu_items`
  ADD PRIMARY KEY (`item_id`),
  ADD KEY `fk_menu_item_restaurant` (`restaurant_id`),
  ADD KEY `fk_menu_item_category` (`menu_category_id`);

--
-- Indexes for table `tbl_order`
--
ALTER TABLE `tbl_order`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_payment`
--
ALTER TABLE `tbl_payment`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_product`
--
ALTER TABLE `tbl_product`
  ADD PRIMARY KEY (`p_id`),
  ADD KEY `fk_product_business` (`business_id`);

--
-- Indexes for table `tbl_product_variants`
--
ALTER TABLE `tbl_product_variants`
  ADD PRIMARY KEY (`variant_id`),
  ADD KEY `p_id` (`p_id`);

--
-- Indexes for table `tbl_professionals`
--
ALTER TABLE `tbl_professionals`
  ADD PRIMARY KEY (`professional_id`),
  ADD UNIQUE KEY `email` (`email`),
  ADD UNIQUE KEY `user_id` (`user_id`),
  ADD KEY `fk_professional_category` (`category_id`);

--
-- Indexes for table `tbl_professional_categories`
--
ALTER TABLE `tbl_professional_categories`
  ADD PRIMARY KEY (`category_id`),
  ADD UNIQUE KEY `category_name_en` (`category_name_en`);

--
-- Indexes for table `tbl_professional_reviews`
--
ALTER TABLE `tbl_professional_reviews`
  ADD PRIMARY KEY (`review_id`),
  ADD KEY `fk_review_professional` (`professional_id`),
  ADD KEY `fk_review_user` (`user_id`);

--
-- Indexes for table `tbl_professional_services`
--
ALTER TABLE `tbl_professional_services`
  ADD PRIMARY KEY (`service_id`),
  ADD KEY `fk_service_professional` (`professional_id`);

--
-- Indexes for table `tbl_project_votes`
--
ALTER TABLE `tbl_project_votes`
  ADD PRIMARY KEY (`vote_id`),
  ADD UNIQUE KEY `unique_user_project_vote` (`project_id`,`user_id`),
  ADD KEY `fk_vote_user` (`user_id`);

--
-- Indexes for table `tbl_restaurants`
--
ALTER TABLE `tbl_restaurants`
  ADD PRIMARY KEY (`restaurant_id`),
  ADD UNIQUE KEY `email` (`email`);

--
-- Indexes for table `tbl_review`
--
ALTER TABLE `tbl_review`
  ADD PRIMARY KEY (`review_id`);

--
-- Indexes for table `tbl_review_image`
--
ALTER TABLE `tbl_review_image`
  ADD PRIMARY KEY (`image_id`),
  ADD KEY `review_id` (`review_id`);

--
-- Indexes for table `tbl_settings`
--
ALTER TABLE `tbl_settings`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_slider`
--
ALTER TABLE `tbl_slider`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_social`
--
ALTER TABLE `tbl_social`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_subscriber`
--
ALTER TABLE `tbl_subscriber`
  ADD PRIMARY KEY (`subs_id`);

--
-- Indexes for table `tbl_top_category`
--
ALTER TABLE `tbl_top_category`
  ADD PRIMARY KEY (`tcat_id`);

--
-- Indexes for table `tbl_transport_bookings`
--
ALTER TABLE `tbl_transport_bookings`
  ADD PRIMARY KEY (`booking_id`),
  ADD KEY `fk_booking_user` (`user_id`),
  ADD KEY `fk_booking_driver` (`driver_id`);

--
-- Indexes for table `tbl_travel_agencies`
--
ALTER TABLE `tbl_travel_agencies`
  ADD PRIMARY KEY (`agency_id`),
  ADD UNIQUE KEY `contact_email` (`contact_email`),
  ADD KEY `fk_agency_owner` (`owner_user_id`);

--
-- Indexes for table `tbl_travel_bookings`
--
ALTER TABLE `tbl_travel_bookings`
  ADD PRIMARY KEY (`booking_id`),
  ADD KEY `fk_travel_booking_user` (`user_id`),
  ADD KEY `fk_travel_booking_agency` (`agency_id`);

--
-- Indexes for table `tbl_used_products`
--
ALTER TABLE `tbl_used_products`
  ADD PRIMARY KEY (`used_product_id`),
  ADD KEY `fk_used_product_seller` (`seller_user_id`);

--
-- Indexes for table `tbl_user`
--
ALTER TABLE `tbl_user`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tbl_vehicles`
--
ALTER TABLE `tbl_vehicles`
  ADD PRIMARY KEY (`vehicle_id`),
  ADD UNIQUE KEY `license_plate` (`license_plate`),
  ADD KEY `fk_vehicle_driver` (`driver_id`);

--
-- Indexes for table `tbl_vouchers`
--
ALTER TABLE `tbl_vouchers`
  ADD PRIMARY KEY (`voucher_id`),
  ADD UNIQUE KEY `voucher_code` (`voucher_code`),
  ADD KEY `fk_voucher_business` (`business_id`);

--
-- Indexes for table `tbl_wishlist`
--
ALTER TABLE `tbl_wishlist`
  ADD PRIMARY KEY (`wishlist_id`),
  ADD UNIQUE KEY `uc_cust_product` (`cust_id`,`product_id`),
  ADD KEY `fk_wishlist_product` (`product_id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `tbl_advertisements`
--
ALTER TABLE `tbl_advertisements`
  MODIFY `ad_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_auctions`
--
ALTER TABLE `tbl_auctions`
  MODIFY `auction_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_auction_bids`
--
ALTER TABLE `tbl_auction_bids`
  MODIFY `bid_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_businesses`
--
ALTER TABLE `tbl_businesses`
  MODIFY `business_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=9;

--
-- AUTO_INCREMENT for table `tbl_coin_transactions`
--
ALTER TABLE `tbl_coin_transactions`
  MODIFY `transaction_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_coupon`
--
ALTER TABLE `tbl_coupon`
  MODIFY `coupon_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `tbl_customer`
--
ALTER TABLE `tbl_customer`
  MODIFY `cust_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=25;

--
-- AUTO_INCREMENT for table `tbl_customer_carts`
--
ALTER TABLE `tbl_customer_carts`
  MODIFY `cart_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=20;

--
-- AUTO_INCREMENT for table `tbl_drivers`
--
ALTER TABLE `tbl_drivers`
  MODIFY `driver_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_emergency_contacts`
--
ALTER TABLE `tbl_emergency_contacts`
  MODIFY `contact_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_features`
--
ALTER TABLE `tbl_features`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=5;

--
-- AUTO_INCREMENT for table `tbl_food_orders`
--
ALTER TABLE `tbl_food_orders`
  MODIFY `order_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_food_order_items`
--
ALTER TABLE `tbl_food_order_items`
  MODIFY `order_item_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_gov_projects`
--
ALTER TABLE `tbl_gov_projects`
  MODIFY `project_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_home_sections`
--
ALTER TABLE `tbl_home_sections`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_home_tabs`
--
ALTER TABLE `tbl_home_tabs`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6;

--
-- AUTO_INCREMENT for table `tbl_hotels`
--
ALTER TABLE `tbl_hotels`
  MODIFY `hotel_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_hotel_rooms`
--
ALTER TABLE `tbl_hotel_rooms`
  MODIFY `room_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_house_rentals`
--
ALTER TABLE `tbl_house_rentals`
  MODIFY `rental_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_house_sales`
--
ALTER TABLE `tbl_house_sales`
  MODIFY `sale_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_kyc_verifications`
--
ALTER TABLE `tbl_kyc_verifications`
  MODIFY `kyc_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `tbl_menu_categories`
--
ALTER TABLE `tbl_menu_categories`
  MODIFY `menu_category_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_menu_items`
--
ALTER TABLE `tbl_menu_items`
  MODIFY `item_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_order`
--
ALTER TABLE `tbl_order`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=138;

--
-- AUTO_INCREMENT for table `tbl_payment`
--
ALTER TABLE `tbl_payment`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=89;

--
-- AUTO_INCREMENT for table `tbl_product_variants`
--
ALTER TABLE `tbl_product_variants`
  MODIFY `variant_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_professionals`
--
ALTER TABLE `tbl_professionals`
  MODIFY `professional_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_professional_categories`
--
ALTER TABLE `tbl_professional_categories`
  MODIFY `category_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_professional_reviews`
--
ALTER TABLE `tbl_professional_reviews`
  MODIFY `review_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_professional_services`
--
ALTER TABLE `tbl_professional_services`
  MODIFY `service_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_project_votes`
--
ALTER TABLE `tbl_project_votes`
  MODIFY `vote_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_restaurants`
--
ALTER TABLE `tbl_restaurants`
  MODIFY `restaurant_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_review`
--
ALTER TABLE `tbl_review`
  MODIFY `review_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_review_image`
--
ALTER TABLE `tbl_review_image`
  MODIFY `image_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_slider`
--
ALTER TABLE `tbl_slider`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `tbl_social`
--
ALTER TABLE `tbl_social`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=17;

--
-- AUTO_INCREMENT for table `tbl_subscriber`
--
ALTER TABLE `tbl_subscriber`
  MODIFY `subs_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

--
-- AUTO_INCREMENT for table `tbl_top_category`
--
ALTER TABLE `tbl_top_category`
  MODIFY `tcat_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

--
-- AUTO_INCREMENT for table `tbl_transport_bookings`
--
ALTER TABLE `tbl_transport_bookings`
  MODIFY `booking_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_travel_agencies`
--
ALTER TABLE `tbl_travel_agencies`
  MODIFY `agency_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_travel_bookings`
--
ALTER TABLE `tbl_travel_bookings`
  MODIFY `booking_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_used_products`
--
ALTER TABLE `tbl_used_products`
  MODIFY `used_product_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_user`
--
ALTER TABLE `tbl_user`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=3;

--
-- AUTO_INCREMENT for table `tbl_vehicles`
--
ALTER TABLE `tbl_vehicles`
  MODIFY `vehicle_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_vouchers`
--
ALTER TABLE `tbl_vouchers`
  MODIFY `voucher_id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `tbl_wishlist`
--
ALTER TABLE `tbl_wishlist`
  MODIFY `wishlist_id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=7;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `tbl_advertisements`
--
ALTER TABLE `tbl_advertisements`
  ADD CONSTRAINT `fk_ad_advertiser` FOREIGN KEY (`advertiser_user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_auctions`
--
ALTER TABLE `tbl_auctions`
  ADD CONSTRAINT `fk_auction_highest_bidder` FOREIGN KEY (`highest_bidder_user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_auction_seller` FOREIGN KEY (`seller_user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_auction_bids`
--
ALTER TABLE `tbl_auction_bids`
  ADD CONSTRAINT `fk_bid_auction` FOREIGN KEY (`auction_id`) REFERENCES `tbl_auctions` (`auction_id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_bid_bidder` FOREIGN KEY (`bidder_user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_businesses`
--
ALTER TABLE `tbl_businesses`
  ADD CONSTRAINT `fk_business_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_coin_transactions`
--
ALTER TABLE `tbl_coin_transactions`
  ADD CONSTRAINT `fk_coin_transaction_user` FOREIGN KEY (`user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_customer_carts`
--
ALTER TABLE `tbl_customer_carts`
  ADD CONSTRAINT `tbl_customer_carts_ibfk_1` FOREIGN KEY (`customer_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_drivers`
--
ALTER TABLE `tbl_drivers`
  ADD CONSTRAINT `fk_driver_user` FOREIGN KEY (`user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE SET NULL;

--
-- Constraints for table `tbl_food_orders`
--
ALTER TABLE `tbl_food_orders`
  ADD CONSTRAINT `fk_food_order_driver` FOREIGN KEY (`driver_id`) REFERENCES `tbl_drivers` (`driver_id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_food_order_restaurant` FOREIGN KEY (`restaurant_id`) REFERENCES `tbl_restaurants` (`restaurant_id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_food_order_user` FOREIGN KEY (`user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_food_order_items`
--
ALTER TABLE `tbl_food_order_items`
  ADD CONSTRAINT `fk_order_item_item` FOREIGN KEY (`item_id`) REFERENCES `tbl_menu_items` (`item_id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_order_item_order` FOREIGN KEY (`order_id`) REFERENCES `tbl_food_orders` (`order_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_hotels`
--
ALTER TABLE `tbl_hotels`
  ADD CONSTRAINT `fk_hotel_agency` FOREIGN KEY (`travel_agency_id`) REFERENCES `tbl_travel_agencies` (`agency_id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_hotel_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE SET NULL;

--
-- Constraints for table `tbl_hotel_rooms`
--
ALTER TABLE `tbl_hotel_rooms`
  ADD CONSTRAINT `fk_room_hotel` FOREIGN KEY (`hotel_id`) REFERENCES `tbl_hotels` (`hotel_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_house_rentals`
--
ALTER TABLE `tbl_house_rentals`
  ADD CONSTRAINT `fk_rental_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_house_sales`
--
ALTER TABLE `tbl_house_sales`
  ADD CONSTRAINT `fk_sale_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_kyc_verifications`
--
ALTER TABLE `tbl_kyc_verifications`
  ADD CONSTRAINT `tbl_kyc_verifications_ibfk_1` FOREIGN KEY (`cust_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_menu_categories`
--
ALTER TABLE `tbl_menu_categories`
  ADD CONSTRAINT `fk_menu_category_restaurant` FOREIGN KEY (`restaurant_id`) REFERENCES `tbl_restaurants` (`restaurant_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_menu_items`
--
ALTER TABLE `tbl_menu_items`
  ADD CONSTRAINT `fk_menu_item_category` FOREIGN KEY (`menu_category_id`) REFERENCES `tbl_menu_categories` (`menu_category_id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_menu_item_restaurant` FOREIGN KEY (`restaurant_id`) REFERENCES `tbl_restaurants` (`restaurant_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_product`
--
ALTER TABLE `tbl_product`
  ADD CONSTRAINT `fk_product_business` FOREIGN KEY (`business_id`) REFERENCES `tbl_businesses` (`business_id`) ON DELETE SET NULL;

--
-- Constraints for table `tbl_product_variants`
--
ALTER TABLE `tbl_product_variants`
  ADD CONSTRAINT `tbl_product_variants_ibfk_1` FOREIGN KEY (`p_id`) REFERENCES `tbl_product` (`p_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_professionals`
--
ALTER TABLE `tbl_professionals`
  ADD CONSTRAINT `fk_professional_category` FOREIGN KEY (`category_id`) REFERENCES `tbl_professional_categories` (`category_id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_professional_user` FOREIGN KEY (`user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE SET NULL;

--
-- Constraints for table `tbl_professional_reviews`
--
ALTER TABLE `tbl_professional_reviews`
  ADD CONSTRAINT `fk_review_professional` FOREIGN KEY (`professional_id`) REFERENCES `tbl_professionals` (`professional_id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_review_user` FOREIGN KEY (`user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_professional_services`
--
ALTER TABLE `tbl_professional_services`
  ADD CONSTRAINT `fk_service_professional` FOREIGN KEY (`professional_id`) REFERENCES `tbl_professionals` (`professional_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_project_votes`
--
ALTER TABLE `tbl_project_votes`
  ADD CONSTRAINT `fk_vote_project` FOREIGN KEY (`project_id`) REFERENCES `tbl_gov_projects` (`project_id`) ON DELETE CASCADE,
  ADD CONSTRAINT `fk_vote_user` FOREIGN KEY (`user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_review_image`
--
ALTER TABLE `tbl_review_image`
  ADD CONSTRAINT `tbl_review_image_ibfk_1` FOREIGN KEY (`review_id`) REFERENCES `tbl_review` (`review_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_transport_bookings`
--
ALTER TABLE `tbl_transport_bookings`
  ADD CONSTRAINT `fk_booking_driver` FOREIGN KEY (`driver_id`) REFERENCES `tbl_drivers` (`driver_id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_booking_user` FOREIGN KEY (`user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_travel_agencies`
--
ALTER TABLE `tbl_travel_agencies`
  ADD CONSTRAINT `fk_agency_owner` FOREIGN KEY (`owner_user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_travel_bookings`
--
ALTER TABLE `tbl_travel_bookings`
  ADD CONSTRAINT `fk_travel_booking_agency` FOREIGN KEY (`agency_id`) REFERENCES `tbl_travel_agencies` (`agency_id`) ON DELETE SET NULL,
  ADD CONSTRAINT `fk_travel_booking_user` FOREIGN KEY (`user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_used_products`
--
ALTER TABLE `tbl_used_products`
  ADD CONSTRAINT `fk_used_product_seller` FOREIGN KEY (`seller_user_id`) REFERENCES `tbl_customer` (`cust_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_vehicles`
--
ALTER TABLE `tbl_vehicles`
  ADD CONSTRAINT `fk_vehicle_driver` FOREIGN KEY (`driver_id`) REFERENCES `tbl_drivers` (`driver_id`) ON DELETE CASCADE;

--
-- Constraints for table `tbl_vouchers`
--
ALTER TABLE `tbl_vouchers`
  ADD CONSTRAINT `fk_voucher_business` FOREIGN KEY (`business_id`) REFERENCES `tbl_businesses` (`business_id`) ON DELETE SET NULL;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
