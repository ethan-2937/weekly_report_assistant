-- Weekly Report Assistant authentication schema.
-- The Spring Boot service creates these tables automatically on startup.

create table if not exists sys_user (
  id bigint primary key auto_increment,
  username varchar(64) not null,
  password_hash varchar(255) null,
  real_name varchar(64) null,
  mobile varchar(32) null,
  email varchar(128) null,
  ding_user_id varchar(128) null,
  ding_union_id varchar(128) null,
  status tinyint not null default 1,
  last_login_time datetime null,
  created_at datetime not null default current_timestamp,
  updated_at datetime not null default current_timestamp on update current_timestamp,
  unique key uk_sys_user_username (username),
  unique key uk_sys_user_ding_user_id (ding_user_id),
  unique key uk_sys_user_ding_union_id (ding_union_id),
  key idx_sys_user_status (status)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_role (
  id bigint primary key auto_increment,
  role_code varchar(32) not null,
  role_name varchar(64) not null,
  description varchar(255) null,
  created_at datetime not null default current_timestamp,
  unique key uk_sys_role_code (role_code)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_user_role (
  user_id bigint not null,
  role_id bigint not null,
  created_at datetime not null default current_timestamp,
  primary key (user_id, role_id),
  key idx_sys_user_role_role_id (role_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_dept_scope (
  id bigint primary key auto_increment,
  user_id bigint not null,
  dept_id varchar(64) null,
  dept_name varchar(128) null,
  scope_type varchar(32) not null default 'DEPT',
  created_at datetime not null default current_timestamp,
  key idx_sys_dept_scope_user_id (user_id)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;

create table if not exists sys_login_log (
  id bigint primary key auto_increment,
  user_id bigint null,
  username varchar(64) null,
  login_type varchar(32) not null,
  success tinyint not null,
  ip varchar(64) null,
  user_agent varchar(512) null,
  message varchar(512) null,
  created_at datetime not null default current_timestamp,
  key idx_sys_login_log_user_id (user_id),
  key idx_sys_login_log_created_at (created_at)
) engine=InnoDB default charset=utf8mb4 collate=utf8mb4_unicode_ci;
