// Copyright (c) YugaByte, Inc.

package com.yugabyte.yw.models;

import com.fasterxml.jackson.databind.JsonNode;
import com.yugabyte.yw.common.FakeDBApplication;
import com.yugabyte.yw.common.ModelFactory;
import com.yugabyte.yw.common.RegexMatcher;
import com.yugabyte.yw.forms.BackupTableParams;
import org.junit.Before;
import org.junit.Test;
import play.Mode;
import play.libs.Json;

import java.util.List;
import java.util.UUID;

import static com.yugabyte.yw.models.Backup.BackupState.Completed;
import static com.yugabyte.yw.models.Backup.BackupState.Deleted;
import static com.yugabyte.yw.models.Backup.BackupState.Failed;
import static com.yugabyte.yw.models.Backup.BackupState.InProgress;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;


public class BackupTest extends FakeDBApplication {
  private Customer defaultCustomer;
  private CustomerConfig s3StorageConfig;

  @Before
  public void setUp() {
    defaultCustomer = ModelFactory.testCustomer();
    s3StorageConfig = ModelFactory.createS3StorageConfig(defaultCustomer);

  }

  private Backup createBackup(UUID universeUUID) {
    BackupTableParams params = new BackupTableParams();
    params.storageConfigUUID = s3StorageConfig.configUUID;
    params.universeUUID = universeUUID;
    params.keyspace = "foo";
    params.tableName = "bar";
    params.tableUUID = UUID.randomUUID();
    return Backup.create(defaultCustomer.uuid, params);
  }

  @Test
  public void testCreate() {
    UUID universeUUID = UUID.randomUUID();
    Backup b = createBackup(universeUUID);
    assertNotNull(b);
    String storageRegex = "s3://foo/univ-" + universeUUID +
        "/backup-\\d{4}-[0-1]\\d-[0-3]\\dT[0-2]\\d:[0-5]\\d:[0-5]\\d\\-\\d+/table-foo.bar-[a-zA-Z0-9]*";
    assertThat(b.getBackupInfo().storageLocation, RegexMatcher.matchesRegex(storageRegex));
    assertEquals(s3StorageConfig.configUUID, b.getBackupInfo().storageConfigUUID);
    assertEquals(InProgress, b.state);
  }

  @Test
  public void testCreateWithoutTableUUID() {
    UUID universeUUID = UUID.randomUUID();
    BackupTableParams params = new BackupTableParams();
    params.storageConfigUUID = s3StorageConfig.configUUID;
    params.universeUUID = universeUUID;
    params.keyspace = "foo";
    params.tableName = "bar";
    Backup b = Backup.create(defaultCustomer.uuid, params);
    String storageRegex = "s3://foo/univ-" + universeUUID +
        "/backup-\\d{4}-[0-1]\\d-[0-3]\\dT[0-2]\\d:[0-5]\\d:[0-5]\\d\\-\\d+/table-foo.bar";
    assertThat(b.getBackupInfo().storageLocation, RegexMatcher.matchesRegex(storageRegex));
    assertEquals(s3StorageConfig.configUUID, b.getBackupInfo().storageConfigUUID);
    assertEquals(InProgress, b.state);
  }

  @Test
  public void testCreateWithNonS3StorageUUID() {
    JsonNode formData = Json.parse("{\"name\": \"FILE\", \"type\": \"STORAGE\", \"data\": \"{}\"}");
    CustomerConfig customerConfig = CustomerConfig.createWithFormData(defaultCustomer.uuid, formData);
    UUID universeUUID = UUID.randomUUID();
    BackupTableParams params = new BackupTableParams();
    params.storageConfigUUID = customerConfig.configUUID;
    params.universeUUID = universeUUID;
    params.keyspace = "foo";
    params.tableName = "bar";
    Backup b = Backup.create(defaultCustomer.uuid, params);
    String storageRegex = "univ-" + universeUUID +
        "/backup-\\d{4}-[0-1]\\d-[0-3]\\dT[0-2]\\d:[0-5]\\d:[0-5]\\d\\-\\d+/table-foo.bar";
    assertThat(b.getBackupInfo().storageLocation, RegexMatcher.matchesRegex(storageRegex));
    assertEquals(customerConfig.configUUID, b.getBackupInfo().storageConfigUUID);
    assertEquals(InProgress, b.state);
  }

  @Test
  public void testFetchByUniverseWithValidUUID() {
    Universe u = ModelFactory.createUniverse(defaultCustomer.getCustomerId());
    createBackup(u.universeUUID);
    List<Backup> backupList = Backup.fetchByUniverseUUID(defaultCustomer.uuid, u.universeUUID);
    assertEquals(1, backupList.size());
  }

  @Test
  public void testFetchByUniverseWithInvalidUUID() {
    Universe u = ModelFactory.createUniverse(defaultCustomer.getCustomerId());
    createBackup(u.universeUUID);
    List<Backup> backupList = Backup.fetchByUniverseUUID(defaultCustomer.uuid, UUID.randomUUID());
    assertEquals(0, backupList.size());
  }


  @Test
  public void testFetchByTaskWithValidUUID() {
    Universe u = ModelFactory.createUniverse(defaultCustomer.getCustomerId());
    Backup b = createBackup(u.universeUUID);
    CustomerTask ct = CustomerTask.create(
        defaultCustomer,
        b.backupUUID,
        UUID.randomUUID(),
        CustomerTask.TargetType.Backup,
        CustomerTask.TaskType.Create,
        "Demo Backup");
    Backup fb = Backup.fetchByTaskUUID(ct.getTaskUUID());
    assertNotNull(fb);
    assertEquals(fb, b);
  }

  @Test
  public void testFetchByTaskWithInvalidBackupUUID() {
    CustomerTask ct = CustomerTask.create(
        defaultCustomer,
        UUID.randomUUID(),
        UUID.randomUUID(),
        CustomerTask.TargetType.Backup,
        CustomerTask.TaskType.Create,
        "Demo Backup");
    Backup fb = Backup.fetchByTaskUUID(ct.getTaskUUID());
    assertNull(fb);
  }

  @Test
  public void testFetchByTaskWithTargetType() {
    Universe u = ModelFactory.createUniverse(defaultCustomer.getCustomerId());
    Backup b = createBackup(u.universeUUID);
    CustomerTask ct = CustomerTask.create(
        defaultCustomer,
        b.backupUUID,
        UUID.randomUUID(),
        CustomerTask.TargetType.Table,
        CustomerTask.TaskType.Create,
        "Demo Backup");
    Backup fb = Backup.fetchByTaskUUID(ct.getTaskUUID());
    assertNull(fb);
  }

  @Test
  public void testGetWithValidCustomerUUID() {
    Universe u = ModelFactory.createUniverse(defaultCustomer.getCustomerId());
    Backup b = createBackup(u.universeUUID);
    Backup fb = Backup.get(defaultCustomer.uuid, b.backupUUID);
    assertEquals(fb, b);
  }

  @Test
  public void testGetWithInvalidCustomerUUID() {
    Universe u = ModelFactory.createUniverse(defaultCustomer.getCustomerId());
    Backup b = createBackup(u.universeUUID);
    Backup fb = Backup.get(UUID.randomUUID(), b.backupUUID);
    assertNull(fb);
  }


  @Test
  public void testTransitionStateValid() {
    Universe u = ModelFactory.createUniverse(defaultCustomer.getCustomerId());
    Backup b = createBackup(u.universeUUID);
    assertNull(b.getUpdateTime());
    b.transitionState(Backup.BackupState.Completed);
    assertEquals(Completed, b.state);
    assertNotNull(b.getUpdateTime());
    b.transitionState(Deleted);
    assertEquals(Deleted, b.state);
  }

  @Test
  public void testTransitionStateInvalid() {
    Universe u = ModelFactory.createUniverse(defaultCustomer.getCustomerId());
    Backup b = createBackup(u.universeUUID);
    assertNull(b.getUpdateTime());
    b.transitionState(Backup.BackupState.Completed);
    assertNotNull(b.getUpdateTime());
    b.transitionState(Failed);
    assertNotEquals(Failed, b.state);
  }
}
