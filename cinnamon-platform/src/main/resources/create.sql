CREATE ROLE ki_aim_user WITH PASSWORD 'changeme' LOGIN;
CREATE DATABASE ki_aim_db OWNER ki_aim_user;
CREATE DATABASE ki_aim_test_db OWNER ki_aim_user;
