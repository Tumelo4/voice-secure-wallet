package com.voicesecure.support;
import java.nio.file.Files;
import java.nio.file.Path;
public final class SupportProductionSchemaTests {
 public static void main(String[] args)throws Exception{String sql=Files.readString(Path.of("services/support-service/src/main/resources/db/migration/V001__support_storage.sql"));
  require(sql,"CREATE TABLE support_transactions");require(sql,"CREATE TABLE support_cases");require(sql,"CREATE TABLE support_audit");require(sql,"CREATE TABLE support_repairs");require(sql,"CREATE TABLE support_repair_postings");System.out.println("Support production schema tests passed: 5");}
 private static void require(String text,String value){if(!text.contains(value))throw new AssertionError(value);}
}
