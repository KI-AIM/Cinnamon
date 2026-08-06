INSERT INTO user_entity (id, username, password) VALUES
    (999,'test_user', '$2a$10$ktXRYklVAt/G5Cg/sWu0de95Chd4LAWhvenTEjQvx.SDGiL2Ls8Mq'); -- Password is 'changeme'

INSERT INTO user_entity_role (user_id, user_role) VALUES
    (999, 'ROLE_USER');
