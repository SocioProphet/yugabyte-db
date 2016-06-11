# --- Created by Ebean DDL
# To stop Ebean DDL generation, remove this comment and start using Evolutions

# --- !Ups

create table availability_zone (
  code                          varchar(25) not null,
  name                          varchar(100) not null,
  region_code                   varchar(25),
  active                        boolean default true not null,
  subnet                        varchar(50) not null,
  constraint pk_availability_zone primary key (code)
);

create table customer (
  uuid                          uuid not null,
  email                         varchar(256) not null,
  password_hash                 varchar(256) not null,
  name                          varchar(256) not null,
  creation_date                 timestamp not null,
  auth_token                    varchar(255),
  auth_token_issue_date         timestamp,
  constraint uq_customer_email unique (email),
  constraint pk_customer primary key (uuid)
);

create table instance (
  instance_id                   uuid not null,
  customer_id                   uuid not null,
  name                          varchar(255) not null,
  customer_uuid                 uuid,
  placement_info                clob not null,
  state                         varchar(3) not null,
  creation_date                 timestamp not null,
  constraint ck_instance_state check (state in ('PRV','SHT','UNK','RUN','CRE','DRP')),
  constraint pk_instance primary key (instance_id,customer_id)
);

create table provider (
  uuid                          uuid not null,
  type                          varchar(3) not null,
  active                        boolean default true not null,
  constraint ck_provider_type check (type in ('AZU','GCE','AWS')),
  constraint uq_provider_type unique (type),
  constraint pk_provider primary key (uuid)
);

create table region (
  code                          varchar(25) not null,
  name                          varchar(100) not null,
  provider_uuid                 uuid,
  active                        boolean default true not null,
  constraint pk_region primary key (code)
);

create table task_info (
  uuid                          uuid not null,
  task_type                     varchar(15) not null,
  task_state                    varchar(7) not null,
  percent_done                  integer default 0,
  details                       clob not null,
  owner                         varchar(255) not null,
  create_time                   timestamp not null,
  update_time                   timestamp not null,
  constraint ck_task_info_task_type check (task_type in ('DestroyInstance','CreateInstance')),
  constraint ck_task_info_task_state check (task_state in ('Running','Success','Failure','Created')),
  constraint pk_task_info primary key (uuid)
);

alter table availability_zone add constraint fk_availability_zone_region_code foreign key (region_code) references region (code) on delete restrict on update restrict;
create index ix_availability_zone_region_code on availability_zone (region_code);

alter table instance add constraint fk_instance_customer_uuid foreign key (customer_uuid) references customer (uuid) on delete restrict on update restrict;
create index ix_instance_customer_uuid on instance (customer_uuid);

alter table region add constraint fk_region_provider_uuid foreign key (provider_uuid) references provider (uuid) on delete restrict on update restrict;
create index ix_region_provider_uuid on region (provider_uuid);


# --- !Downs

alter table availability_zone drop constraint if exists fk_availability_zone_region_code;
drop index if exists ix_availability_zone_region_code;

alter table instance drop constraint if exists fk_instance_customer_uuid;
drop index if exists ix_instance_customer_uuid;

alter table region drop constraint if exists fk_region_provider_uuid;
drop index if exists ix_region_provider_uuid;

drop table if exists availability_zone;

drop table if exists customer;

drop table if exists instance;

drop table if exists provider;

drop table if exists region;

drop table if exists task_info;
