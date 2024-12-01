DELIMITER $$
CREATE DEFINER=`root`@`%` PROCEDURE `add_online_user`(user VARCHAR(32), count INT)
BEGIN
	DECLARE counter INT DEFAULT 0;
	for_loop: LOOP
		IF counter >= count THEN
			LEAVE for_loop;
        END IF;
        
		INSERT INTO gz.sessions VALUES (md5(rand()), "billiards", user, NOW(), -1, -1);
        SET counter = counter + 1;
	END LOOP for_loop;
END$$
DELIMITER ;

DELIMITER $$
CREATE DEFINER=`root`@`%` PROCEDURE `remove_online_user`(user VARCHAR(32))
BEGIN
	DELETE FROM gz.sessions WHERE gzid=user;
END$$
DELIMITER ;
