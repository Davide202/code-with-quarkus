DROP SCHEMA  if exists quarkus;
create schema quarkus;
CREATE TABLE `quarkus`.`tb_account_quarkus` (
  `id` BIGINT NOT NULL,
  `ACCOUNT_USERNAME` VARCHAR(45) NULL,
  `ACCOUNT_PASSWORD` VARCHAR(45) NULL,
  PRIMARY KEY (`id`));

  CREATE TABLE `quarkus`.`tb_user_quarkus` (
    `id` BIGINT NOT NULL,
    `USER_ACCOUNT` BIGINT NOT NULL,
    `USER_NOME` VARCHAR(45) NULL,
    `USER_COGNOME` VARCHAR(45) NULL,
    PRIMARY KEY (`id`),
    INDEX `user_account_idx` (`USER_ACCOUNT` ASC) VISIBLE,
    CONSTRAINT `user_account`
      FOREIGN KEY (`USER_ACCOUNT`)
      REFERENCES `quarkus`.`tb_account_quarkus` (`id`)
      ON DELETE NO ACTION
      ON UPDATE NO ACTION);