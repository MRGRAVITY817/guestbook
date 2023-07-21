CREATE TABLE posts
(id SERIAL PRIMARY KEY,
 name TEXT not null,
 message TEXT not null,
 timestamp TIMESTAMP not null DEFAULT now());
