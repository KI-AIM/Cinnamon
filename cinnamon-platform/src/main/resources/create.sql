CREATE ROLE cinnamon_user WITH PASSWORD 'changeme' LOGIN;
CREATE DATABASE cinnamon_db OWNER cinnamon_user;
CREATE DATABASE cinnamon_test_db OWNER cinnamon_user;
