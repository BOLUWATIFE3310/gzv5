CREATE TABLE `access_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `gzid` varchar(32) NOT NULL,
  `when` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `compid` varchar(32) DEFAULT NULL,
  `ip` varchar(32) DEFAULT NULL,
  `game` varchar(32) NOT NULL,
  `room` int(11) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `access_log.gzid_idx` (`gzid`),
  KEY `acces_log.compid_idx` (`compid`),
  KEY `access_log.ip_idx` (`ip`),
  CONSTRAINT `access_log.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=341901 DEFAULT CHARSET=utf8;

CREATE TABLE `admin_changes` (
  `id` int(11) NOT NULL,
  `admin` varchar(32) DEFAULT NULL,
  `author` varchar(32) DEFAULT NULL,
  `when` timestamp NULL DEFAULT NULL,
  `type` int(11) DEFAULT NULL,
  `level` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `admin_changes.admin_idx` (`admin`),
  KEY `admin_changes.author_idx` (`author`),
  CONSTRAINT `admin_changes.admin` FOREIGN KEY (`admin`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `admin_changes.author` FOREIGN KEY (`author`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `admins` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `game` varchar(32) DEFAULT NULL,
  `room` int(11) DEFAULT NULL,
  `gzid` varchar(32) NOT NULL,
  `level` int(11) NOT NULL DEFAULT '0',
  `added_by` varchar(32) DEFAULT NULL,
  `added_when` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `active` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `admins.game_room_gzid_unq` (`game`,`room`,`gzid`),
  KEY `admins.room_idx` (`room`),
  KEY `admins.gzid_idx` (`gzid`),
  KEY `admins.added_by_idx` (`added_by`),
  KEY `admins.game_idx` (`game`),
  KEY `admins.game_gzid_idx` (`game`,`gzid`),
  CONSTRAINT `admins.added_by` FOREIGN KEY (`added_by`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `admins.game` FOREIGN KEY (`game`) REFERENCES `games` (`name`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `admins.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `admins.room` FOREIGN KEY (`room`) REFERENCES `rooms` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=22 DEFAULT CHARSET=utf8;

CREATE TABLE `bad_words` (
  `word` varchar(64) NOT NULL,
  `lang` varchar(6) DEFAULT NULL,
  `added_by` varchar(32) DEFAULT NULL,
  `added_when` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `bans` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `game` varchar(32) DEFAULT NULL,
  `room` int(11) DEFAULT NULL,
  `gzid` varchar(32) DEFAULT NULL,
  `compid` varchar(32) DEFAULT NULL,
  `ip` varchar(32) DEFAULT NULL,
  `type` set('GZID','COMPID','IP') DEFAULT 'GZID',
  `restriction` enum('CHAT','ACCESS') DEFAULT 'ACCESS',
  `admin` varchar(64) DEFAULT NULL,
  `when` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `duration` bigint(20) DEFAULT '-1',
  `reason` text,
  `unbanned_when` timestamp NULL DEFAULT NULL,
  `unbanned_by` varchar(32) DEFAULT NULL,
  `unbanned_reason` text,
  PRIMARY KEY (`id`),
  KEY `bans.game_idx` (`game`),
  KEY `bans.room_idx` (`room`),
  KEY `bans.gzid_idx` (`gzid`),
  KEY `bans.compid_idx` (`compid`),
  KEY `bans.ip_idx` (`ip`),
  KEY `bans.when_idx` (`when`),
  CONSTRAINT `bans.game` FOREIGN KEY (`game`) REFERENCES `games` (`name`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `bans.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `bans.room` FOREIGN KEY (`room`) REFERENCES `rooms` (`id`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=589 DEFAULT CHARSET=utf8;

CREATE TABLE `captchas` (
  `vn` varchar(32) NOT NULL,
  `vs` text,
  `vt` varchar(3) DEFAULT NULL,
  `created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`vn`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `compids` (
  `compid` varchar(32) NOT NULL,
  `times_used` int(11) DEFAULT '0',
  `last_time_used_by` varchar(32) NOT NULL,
  `last_time_used_when` timestamp NULL DEFAULT NULL,
  `banned` bit(1) DEFAULT b'0',
  `banned_when` timestamp NULL DEFAULT NULL,
  `ban_duration` int(11) DEFAULT NULL,
  `ban_reason` text,
  PRIMARY KEY (`compid`),
  KEY `compids.last_time_used_by_idx` (`last_time_used_by`),
  CONSTRAINT `compids.last_time_used_by` FOREIGN KEY (`last_time_used_by`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `countries` (
  `id` varchar(2) NOT NULL,
  `lang` varchar(6) DEFAULT NULL,
  `local_name` text,
  `english_name` text,
  PRIMARY KEY (`id`),
  KEY `countries.lang_idx` (`lang`),
  CONSTRAINT `countries.lang` FOREIGN KEY (`lang`) REFERENCES `langs` (`id`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `emails` (
  `email` varchar(64) NOT NULL,
  `times_using` int(11) DEFAULT '0',
  `times_used` int(11) DEFAULT '0',
  `last_time_used_by` varchar(32) NOT NULL,
  `last_time_used_when` timestamp NULL DEFAULT NULL,
  `banned` bit(1) DEFAULT b'0',
  `banned_when` timestamp NULL DEFAULT NULL,
  `ban_duration` int(11) unsigned DEFAULT NULL,
  `ban_reason` text,
  PRIMARY KEY (`email`),
  KEY `emails.last_time_used_by_idx` (`last_time_used_by`),
  CONSTRAINT `emails.last_time_used_by` FOREIGN KEY (`last_time_used_by`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `files` (
  `path` varchar(256) NOT NULL,
  `last_modified` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`path`)
) ENGINE=MyISAM DEFAULT CHARSET=utf8;

CREATE TABLE `friends` (
  `gzid` varchar(32) NOT NULL,
  `friend` varchar(32) NOT NULL,
  `added_when` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`gzid`,`friend`),
  KEY `friends.friend_idx` (`friend`),
  CONSTRAINT `friends.friend` FOREIGN KEY (`friend`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `friends.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `game_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `game` varchar(32) NOT NULL,
  `variant` varchar(45) NOT NULL,
  `room` int(11) NOT NULL,
  `rated` bit(1) NOT NULL DEFAULT b'1',
  `player1` varchar(32) NOT NULL,
  `player1_nick` text NOT NULL,
  `player1_compid` varchar(32) DEFAULT NULL,
  `player1_ip` varchar(32) DEFAULT NULL,
  `old_rating1` int(11) DEFAULT NULL,
  `old_rating12` int(11) NOT NULL,
  `new_rating1` int(11) DEFAULT NULL,
  `new_rating12` int(11) NOT NULL,
  `avatar1` int(11) NOT NULL,
  `player2` varchar(32) NOT NULL,
  `player2_nick` text NOT NULL,
  `player2_compid` varchar(32) DEFAULT NULL,
  `player2_ip` varchar(32) DEFAULT NULL,
  `old_rating2` int(11) DEFAULT NULL,
  `old_rating22` int(11) NOT NULL,
  `new_rating2` int(11) DEFAULT NULL,
  `new_rating22` int(11) NOT NULL,
  `avatar2` int(11) NOT NULL,
  `played_when` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `duration` int(11) DEFAULT NULL,
  `turns` int(10) unsigned DEFAULT NULL,
  `winner` int(11) NOT NULL,
  `data` blob,
  PRIMARY KEY (`id`),
  KEY `game_log.room_idx` (`room`),
  KEY `game_log.game_idx` (`game`),
  KEY `game_log.player1_idx` (`player1`),
  KEY `game_log.player2_idx` (`player2`),
  KEY `game_log.variant_idx` (`game`,`variant`),
  KEY `game_log.game_player1_player2_idx` (`game`,`player1`,`player2`),
  KEY `game_log.player1_compid` (`player1_compid`),
  KEY `game_log.player1_ip` (`player1_ip`),
  KEY `game_log.player2_compid` (`player2_compid`),
  KEY `game_log.player2_ip` (`player2_ip`),
  CONSTRAINT `game_log.player1` FOREIGN KEY (`player1`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `game_log.player2` FOREIGN KEY (`player2`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `game_log.room` FOREIGN KEY (`room`) REFERENCES `rooms` (`id`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `game_log.variant` FOREIGN KEY (`game`, `variant`) REFERENCES `variants` (`game`, `name`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=288567 DEFAULT CHARSET=utf8;

CREATE TABLE `games` (
  `name` varchar(32) NOT NULL,
  `title` text,
  `description` text,
  `min_rating` int(11) DEFAULT '0',
  `max_rating` int(11) DEFAULT '10000',
  `initial_rating` int(11) DEFAULT '1000',
  `users` int(11) DEFAULT '0',
  `swf_version` text,
  `home_version` text,
  `game_version` text,
  `smenu_version` text,
  `domainhost` text NOT NULL,
  `homename` text NOT NULL,
  `homeurl` text,
  `contenturl` text,
  `forumurl` text,
  `serverurl` text,
  `swfdir` text,
  `active` bit(1) DEFAULT b'1',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `group_members` (
  `group` varchar(32) NOT NULL,
  `gzid` varchar(32) NOT NULL,
  `join_when` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`group`,`gzid`),
  KEY `group_members.gzid_idx` (`gzid`),
  CONSTRAINT `group_members.group` FOREIGN KEY (`group`) REFERENCES `groups` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `group_members.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `groups` (
  `id` varchar(32) NOT NULL,
  `password` text NOT NULL,
  `name` text NOT NULL,
  `created` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `owner` varchar(32) NOT NULL,
  `member_count` int(11) DEFAULT '0',
  `active` int(11) DEFAULT '1',
  PRIMARY KEY (`id`),
  KEY `groups.owner_idx` (`owner`),
  CONSTRAINT `groups.owner` FOREIGN KEY (`owner`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `ignoreds` (
  `gzid` varchar(32) NOT NULL,
  `ignored` varchar(32) NOT NULL,
  `ignored_when` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`gzid`,`ignored`),
  KEY `ignoreds.ignored_idx` (`ignored`),
  CONSTRAINT `ignoreds.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `ignoreds.ignored` FOREIGN KEY (`ignored`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `inventories` (
  `gzid` varchar(32) NOT NULL,
  `item` varchar(32) NOT NULL,
  `type` varchar(32) NOT NULL,
  `added_when` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `added_by` varchar(32) DEFAULT NULL,
  `active` int(11) NOT NULL DEFAULT '1',
  PRIMARY KEY (`gzid`,`item`,`type`),
  KEY `inventories.type_idx` (`item`),
  KEY `inventories.type_idx1` (`type`),
  CONSTRAINT `inventories.item` FOREIGN KEY (`item`) REFERENCES `inventory_items` (`id`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `inventories.type` FOREIGN KEY (`type`) REFERENCES `inventory_items` (`type`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `inventory_items` (
  `id` varchar(32) NOT NULL,
  `type` varchar(32) NOT NULL,
  `game` varchar(32) DEFAULT NULL,
  `name` text NOT NULL,
  PRIMARY KEY (`id`,`type`),
  KEY `inventory_items.type_idx` (`type`),
  KEY `inventory_items.game_idx` (`game`),
  CONSTRAINT `inventory_items.game` FOREIGN KEY (`game`) REFERENCES `games` (`name`) ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `inventory_items.type` FOREIGN KEY (`type`) REFERENCES `inventory_types` (`name`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `inventory_types` (
  `name` varchar(32) NOT NULL,
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `ips` (
  `ip` varchar(32) NOT NULL,
  `times_used` int(11) DEFAULT '0',
  `last_time_used_by` varchar(32) NOT NULL,
  `last_time_used_when` timestamp NULL DEFAULT NULL,
  `banned` bit(1) DEFAULT b'0',
  `banned_when` timestamp NULL DEFAULT NULL,
  `ban_duration` int(11) DEFAULT NULL,
  `ban_reason` text,
  PRIMARY KEY (`ip`),
  KEY `ips.last_time_used_by_idx` (`last_time_used_by`),
  CONSTRAINT `ips.last_time_used_by` FOREIGN KEY (`last_time_used_by`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `langs` (
  `id` varchar(6) NOT NULL,
  `local_name` text,
  `english_name` varchar(45) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `lobby_chat_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `game` varchar(32) CHARACTER SET utf8 NOT NULL,
  `lobby` int(11) DEFAULT NULL,
  `game_id` int(11) DEFAULT NULL,
  `sender` varchar(32) CHARACTER SET utf8 NOT NULL,
  `sender_nick` text CHARACTER SET utf8,
  `receiver` varchar(32) CHARACTER SET utf8 DEFAULT NULL,
  `message` text CHARACTER SET utf8,
  `sent_when` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `lobby_chat_log.sender` (`sender`),
  KEY `lobby_chat_log.receiver` (`receiver`),
  KEY `lobby_chat_log.sent_when` (`sent_when`),
  KEY `lobby_chat_log.game_idx` (`game`),
  KEY `lobby_chat_log.lobby` (`lobby`),
  CONSTRAINT `lobby_chat_log.game` FOREIGN KEY (`game`) REFERENCES `games` (`name`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `lobby_chat_log.receiver` FOREIGN KEY (`receiver`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `lobby_chat_log.sender` FOREIGN KEY (`sender`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=1661821 DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

CREATE TABLE `login_log` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `gzid` varchar(32) NOT NULL,
  `when` timestamp NULL DEFAULT NULL,
  `compid` varchar(32) DEFAULT NULL,
  `ip` varchar(32) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `login_log.gzid_idx` (`gzid`),
  KEY `login_log.compid_idx` (`compid`),
  KEY `login_log.ip_idx` (`ip`),
  CONSTRAINT `login_log.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=187852 DEFAULT CHARSET=utf8;

CREATE TABLE `mail_services` (
  `id` varchar(32) NOT NULL,
  `host` text NOT NULL,
  `port` int(11) NOT NULL DEFAULT '25',
  `sender` text NOT NULL,
  `password` text,
  `use_ssl` int(11) NOT NULL DEFAULT '0',
  `use_auth` int(11) NOT NULL DEFAULT '1',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `nick_log` (
  `nick` varchar(128) CHARACTER SET utf8 NOT NULL,
  `gzid` varchar(32) CHARACTER SET utf8 NOT NULL,
  `times_used` int(11) DEFAULT '0',
  `last_time_used_when` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`nick`,`gzid`),
  KEY `nick_log.users_idx` (`gzid`),
  CONSTRAINT `nick_log.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

CREATE TABLE `passwords` (
  `password` varchar(64) NOT NULL,
  `times_used` int(11) DEFAULT '0',
  `last_time_used_by` varchar(32) NOT NULL,
  `last_time_used_when` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`password`),
  KEY `passwords.last_time_used_by_idx` (`last_time_used_by`),
  CONSTRAINT `passwords.last_time_used_by` FOREIGN KEY (`last_time_used_by`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `premiums` (
  `gzid` varchar(32) NOT NULL,
  `started_when` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `duration` int(10) DEFAULT '-1',
  PRIMARY KEY (`gzid`),
  CONSTRAINT `premiums.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `registers` (
  `gzid` varchar(32) NOT NULL,
  `compid` varchar(32) DEFAULT NULL,
  `ip` varchar(32) NOT NULL,
  `registered_when` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`gzid`),
  KEY `registers.compid_idx` (`compid`),
  KEY `registers.ip_idx` (`ip`),
  KEY `registers.registered_when_idx` (`registered_when`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `rooms` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `game` varchar(32) NOT NULL,
  `name` text NOT NULL,
  `country` varchar(2) DEFAULT NULL,
  `group` varchar(32) DEFAULT NULL,
  `variant` varchar(32) DEFAULT NULL,
  `tournament` varchar(32) DEFAULT NULL,
  `lobby_host` varchar(64) NOT NULL,
  `lobby_port` int(11) NOT NULL,
  `game_host` varchar(64) NOT NULL,
  `game_port` int(11) NOT NULL,
  `max_players` int(11) NOT NULL DEFAULT '120',
  `users` int(11) DEFAULT '0',
  `active` bit(1) NOT NULL DEFAULT b'1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `rooms.lobby_host_port` (`lobby_host`,`lobby_port`),
  UNIQUE KEY `rooms.game_host_port` (`game_host`,`game_port`),
  KEY `rooms.game_idx` (`game`),
  KEY `rooms.group_idx` (`group`),
  KEY `rooms.country_idx` (`country`),
  KEY `rooms.variant_idx` (`game`,`variant`),
  CONSTRAINT `rooms.country` FOREIGN KEY (`country`) REFERENCES `countries` (`id`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `rooms.game` FOREIGN KEY (`game`) REFERENCES `games` (`name`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `rooms.group` FOREIGN KEY (`group`) REFERENCES `groups` (`id`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `rooms.variant` FOREIGN KEY (`game`, `variant`) REFERENCES `variants` (`game`, `name`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB AUTO_INCREMENT=293 DEFAULT CHARSET=utf8;

CREATE TABLE `sessions` (
  `fsid` varchar(32) NOT NULL,
  `game` varchar(32) NOT NULL,
  `gzid` varchar(32) NOT NULL,
  `created` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `expires` int(11) DEFAULT NULL,
  `expires2` int(11) DEFAULT NULL,
  PRIMARY KEY (`fsid`),
  KEY `sessions.game` (`game`),
  KEY `sessions.created` (`created`),
  KEY `sessions.gzid` (`gzid`),
  CONSTRAINT `sessions.game` FOREIGN KEY (`game`) REFERENCES `games` (`name`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `sessions.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `stats` (
  `game` varchar(32) NOT NULL,
  `variant` varchar(32) NOT NULL,
  `gzid` varchar(32) NOT NULL,
  `playeds` int(11) unsigned NOT NULL DEFAULT '0',
  `abandoneds` int(11) unsigned NOT NULL DEFAULT '0',
  `wins` int(11) unsigned NOT NULL DEFAULT '0',
  `losses` int(11) unsigned NOT NULL DEFAULT '0',
  `draws` int(11) unsigned NOT NULL DEFAULT '0',
  `rating` int(10) unsigned NOT NULL DEFAULT '0',
  `rating2` int(11) unsigned NOT NULL DEFAULT '0',
  `streak` int(11) NOT NULL DEFAULT '0',
  PRIMARY KEY (`game`,`variant`,`gzid`),
  KEY `stats.gzid_idx` (`gzid`),
  KEY `stats.game_gzid` (`game`,`gzid`),
  KEY `stats.variant_idx` (`game`,`variant`),
  CONSTRAINT `stats.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `stats.variant` FOREIGN KEY (`game`, `variant`) REFERENCES `variants` (`game`, `name`) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `users` (
  `gzid` varchar(32) CHARACTER SET utf8 NOT NULL,
  `email` varchar(100) CHARACTER SET utf8 NOT NULL,
  `password` text CHARACTER SET utf8 NOT NULL,
  `compid` varchar(32) CHARACTER SET latin1 DEFAULT NULL,
  `registered` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `verified` bit(1) NOT NULL DEFAULT b'0',
  `last_login` int(11) DEFAULT NULL,
  `last_access` int(11) DEFAULT NULL,
  `banned` bit(1) NOT NULL DEFAULT b'0',
  `banned_when` timestamp NULL DEFAULT NULL,
  `ban_duration` int(10) unsigned NOT NULL DEFAULT '0',
  `ban_reason` text CHARACTER SET utf8,
  `system` bit(1) NOT NULL DEFAULT b'0',
  `active` bit(1) NOT NULL DEFAULT b'1',
  `avatar` int(11) DEFAULT '1',
  `lang` varchar(5) CHARACTER SET latin1 DEFAULT 'en',
  `country` varchar(5) CHARACTER SET latin1 DEFAULT 'US',
  `bday` int(2) NOT NULL,
  `bmonth` int(1) NOT NULL,
  `byear` int(4) NOT NULL,
  `allowe` bit(1) DEFAULT b'0',
  `allowp` bit(1) DEFAULT b'0',
  `allowc` bit(1) DEFAULT b'0',
  `gender` bit(1) NOT NULL,
  `qst` int(11) NOT NULL,
  `ans` text CHARACTER SET utf8 NOT NULL,
  `nick` text CHARACTER SET utf8 NOT NULL,
  `stars` int(1) DEFAULT '0',
  `gzm` bigint(20) NOT NULL DEFAULT '0',
  PRIMARY KEY (`gzid`),
  UNIQUE KEY `email_UNIQUE` (`email`),
  KEY `users.last_login_idx` (`last_login`),
  KEY `users.last_access_idx` (`last_access`),
  CONSTRAINT `users.last_access` FOREIGN KEY (`last_access`) REFERENCES `access_log` (`id`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `users.last_login` FOREIGN KEY (`last_login`) REFERENCES `login_log` (`id`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8 COLLATE=utf8_bin;

CREATE TABLE `variants` (
  `game` varchar(32) NOT NULL,
  `name` varchar(32) NOT NULL,
  `title` text,
  PRIMARY KEY (`game`,`name`),
  CONSTRAINT `variants.game` FOREIGN KEY (`game`) REFERENCES `games` (`name`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

CREATE TABLE `views` (
  `gzid` varchar(32) NOT NULL DEFAULT '',
  `viewer` varchar(32) NOT NULL DEFAULT '',
  `count` int(11) NOT NULL DEFAULT '0',
  `last_viewer_compid` int(11) DEFAULT NULL,
  `last_viewer_ip` varchar(32) DEFAULT NULL,
  `last_visit_date` timestamp NULL DEFAULT NULL,
  PRIMARY KEY (`gzid`,`viewer`),
  KEY `views.viewer_idx` (`viewer`),
  CONSTRAINT `views.gzid` FOREIGN KEY (`gzid`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE,
  CONSTRAINT `views.viewer` FOREIGN KEY (`viewer`) REFERENCES `users` (`gzid`) ON DELETE NO ACTION ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8;
