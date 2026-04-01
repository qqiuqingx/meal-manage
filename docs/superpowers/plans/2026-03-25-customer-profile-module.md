# Customer Profile Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a new standalone customer profile module with package-category tree management, aggregate customer save/edit APIs, and Vue admin pages, without modifying the existing `meal/customerDietaryRestrictions` flow.

**Architecture:** Add a new backend module under `me.zhengjie.modules.customer.profile` that owns four tables: customer profile, address slots, current package contract, and package-category tree. Expose two API groups: `/api/customerProfile` for aggregate CRUD and `/api/customerPackageCategory` for tree/category CRUD. On the frontend, add two pages under `views/customer/`: a tree-based package-category page derived from `system/dept`, and a customer profile CRUD page derived from `meal/customerDietaryRestrictions` plus a dedicated form component for aggregate editing.

**Tech Stack:** Spring Boot 2.7, MyBatis-Plus, MyBatis XML, JUnit 5 / spring-boot-starter-test, Vue 2.7, Element UI, vue-treeselect, existing CRUD mixins.

---

## Implementation file map

### Create: backend domain and API
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerProfile.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerProfileAddress.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerProfilePackage.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerPackageCategory.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileQueryCriteria.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileSaveDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileDetailDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileStatusUpdateDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileStatusRequestDto.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerPackageCategoryQueryCriteria.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerProfileMapper.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerProfileAddressMapper.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerProfilePackageMapper.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerPackageCategoryMapper.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/CustomerProfileService.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/CustomerPackageCategoryService.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/impl/CustomerProfileServiceImpl.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/impl/CustomerPackageCategoryServiceImpl.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/rest/CustomerProfileController.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/rest/CustomerPackageCategoryController.java`
- `eladmin/eladmin-system/src/main/resources/mapper/CustomerProfileMapper.xml`
- `eladmin/eladmin-system/src/main/resources/mapper/CustomerPackageCategoryMapper.xml`

### Create: tests
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/CustomerPackageCategoryServiceImplTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/CustomerProfileServiceImplTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/rest/CustomerProfileControllerTest.java`
- `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/integration/CustomerProfileIntegrationTest.java`
- `eladmin-web/tests/e2e/customer-profile.spec.js`

### Create: frontend
- `eladmin-web/src/api/customerPackageCategory.js`
- `eladmin-web/src/api/customerProfile.js`
- `eladmin-web/src/views/customer/packageCategory/index.vue`
- `eladmin-web/src/views/customer/profile/index.vue`
- `eladmin-web/src/views/customer/profile/form.vue`

### Create: docs and SQL
- `eladmin/sql/customer-profile.sql`
- `eladmin/doc/customer-profile-api.md`

### Reference only (do not modify unless implementation proves necessary)
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/domain/CustomerDietaryRestrictions.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/domain/dto/CustomerDietaryRestrictionsQueryCriteria.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/rest/CustomerDietaryRestrictionsController.java`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/service/impl/CustomerDietaryRestrictionsServiceImpl.java`
- `eladmin/eladmin-system/src/main/resources/mapper/CustomerDietaryRestrictionsMapper.xml`
- `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/system/rest/DeptController.java`
- `eladmin-web/src/views/meal/customerDietaryRestrictions/index.vue`
- `eladmin-web/src/views/system/dept/index.vue`
- `eladmin-web/src/api/system/dept.js`

## Key implementation decisions

- Use `modules/customer/profile/...` as the backend package root to match the approved spec.
- Store `allergy_tags` as JSON array using `JacksonTypeHandler`, matching the existing meal module pattern. This is acceptable here because the project already uses this MyBatis JSON mapping approach in business modules even though application-level JSON serialization uses fastjson2.
- Keep address storage as fixed slot rows (`DEFAULT`, `WORKDAY`, `WEEKEND`) instead of a free-form address list.
- Implement profile save as a full aggregate overwrite: submitted `addresses` + `packageInfo` replace current stored state in one transaction.
- Do not build amount/deposit logic in this phase.
- Do not sync to `meal/customer_dietary_restrictions` in this phase.

## Task 1: Add SQL bootstrap and API contract skeleton

**Files:**
- Create: `eladmin/sql/customer-profile.sql`
- Create: `eladmin/doc/customer-profile-api.md`
- Reference: `eladmin/doc/customer-dietary-restrictions-api.md`

- [ ] **Step 1: Draft the DDL and seed skeleton**

```sql
CREATE TABLE customer_profile (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_code VARCHAR(16) NOT NULL UNIQUE,
  customer_name VARCHAR(50) NOT NULL,
  phone VARCHAR(20) NOT NULL,
  gestational_week INT NULL,
  allergy_tags JSON NULL,
  medical_requirements VARCHAR(500) NULL,
  status BIT NOT NULL DEFAULT b'1',
  remark VARCHAR(255) NULL,
  create_by VARCHAR(100) NULL,
  update_by VARCHAR(100) NULL,
  create_time DATETIME NULL,
  update_time DATETIME NULL
);

CREATE TABLE customer_profile_address (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  customer_id BIGINT NOT NULL,
  address_type VARCHAR(20) NOT NULL,
  address_detail VARCHAR(200) NOT NULL,
  contact_name VARCHAR(50) NULL,
  contact_phone VARCHAR(20) NULL,
  create_time DATETIME NULL,
  update_time DATETIME NULL,
  UNIQUE KEY uk_customer_address_type (customer_id, address_type)
);
```

- [ ] **Step 2: Complete the remaining contract in the same SQL file**

Add DDL for:
- `customer_profile_package`
- `customer_package_category`

Include seed data for:
- parent categories `月子餐(A)` and `营养餐(B)`
- at least two child categories for each parent
- required `sys_menu` rows for:
  - `customer/profile/index`
  - `customer/packageCategory/index`
  - button permissions (`list/add/edit/status/del` as needed)

- [ ] **Step 3: Write the API markdown before implementation**

Add sections to `eladmin/doc/customer-profile-api.md` for:
- package category tree API
- profile list/detail API
- profile create/update/status API
- code generation API

Use an example request body like:

```json
{
  "customerCode": "A001",
  "customerName": "张三",
  "phone": "13800000000",
  "addresses": [
    {"addressType": "DEFAULT", "addressDetail": "地址1"}
  ],
  "packageInfo": {
    "parentPackageId": 1,
    "childPackageId": 11,
    "breakfastCount": 10,
    "lunchDinnerCount": 20,
    "startDate": "2026-03-25",
    "endDate": "2026-04-25"
  }
}
```

- [ ] **Step 4: Add the required menu + permission seed rows**

Seed at minimum:
- directory/menu for package category management
- directory/menu for customer profile management
- button permissions for `list/add/edit/status/del` where applicable

Verify `sys_menu.component` values are exactly:
- `customer/packageCategory/index`
- `customer/profile/index`

- [ ] **Step 5: Manually verify SQL and doc alignment against the spec**

Checklist:
- addresses: at least one of `DEFAULT/WORKDAY/WEEKEND`
- counts: one of breakfast or lunch+dinner can be null, but not both
- `total_count` is persisted but backend-computed
- no amount/deposit fields appear anywhere

- [ ] **Step 6: Commit**

```bash
git add eladmin/sql/customer-profile.sql eladmin/doc/customer-profile-api.md
git commit -m "feat: add customer profile schema and api contract"
```

## Task 2: Build package-category backend with tests first

**Files:**
- Create: `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/CustomerPackageCategoryServiceImplTest.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerPackageCategory.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerPackageCategoryQueryCriteria.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerPackageCategoryMapper.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/CustomerPackageCategoryService.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/impl/CustomerPackageCategoryServiceImpl.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/rest/CustomerPackageCategoryController.java`
- Create: `eladmin/eladmin-system/src/main/resources/mapper/CustomerPackageCategoryMapper.xml`
- Reference: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/system/rest/DeptController.java`

- [ ] **Step 1: Write the failing category service tests**

```java
@Test
void shouldRejectDuplicateCodePrefixForEnabledParent() {
    CustomerPackageCategory parent = new CustomerPackageCategory();
    parent.setLevel(1);
    parent.setCodePrefix("A");
    when(categoryMapper.existsEnabledParentPrefix("A", null)).thenReturn(true);

    assertThrows(BadRequestException.class, () -> service.create(parent));
}

@Test
void shouldRejectChildBoundToDisabledParent() {
    CustomerPackageCategory child = new CustomerPackageCategory();
    child.setLevel(2);
    child.setParentId(1L);
    when(categoryMapper.findById(1L)).thenReturn(disabledParent());

    assertThrows(BadRequestException.class, () -> service.create(child));
}
```

- [ ] **Step 2: Run the targeted test and confirm RED**

Run:

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -pl eladmin-system -DskipTests=false -Dtest=CustomerPackageCategoryServiceImplTest test
```

Expected: FAIL because the category service and model do not exist yet.

- [ ] **Step 3: Implement the minimal backend pieces**

Use this shape for the entity:

```java
@Data
@TableName("customer_package_category")
public class CustomerPackageCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String categoryName;
    private String categoryCode;
    private Long parentId;
    private Integer level;
    private Integer sort;
    private Boolean enabled;
    private String codePrefix;
}
```

Controller endpoints to implement:
- `GET /api/customerPackageCategory/tree`
- `GET /api/customerPackageCategory/parents`
- `POST /api/customerPackageCategory`
- `PUT /api/customerPackageCategory`
- `PUT /api/customerPackageCategory/{id}/status`
- `DELETE /api/customerPackageCategory/{id}`

- [ ] **Step 4: Run the tests again and make them GREEN**

Run the same Maven command from Step 2.
Expected: PASS.

- [ ] **Step 5: Smoke-test the tree query manually**

Use Swagger or curl to verify that parent nodes include children and only enabled parents appear in the parent selector endpoint.

- [ ] **Step 6: Commit**

```bash
git add \
  eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/CustomerPackageCategoryServiceImplTest.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerPackageCategory.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerPackageCategoryQueryCriteria.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerPackageCategoryMapper.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/CustomerPackageCategoryService.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/impl/CustomerPackageCategoryServiceImpl.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/rest/CustomerPackageCategoryController.java \
  eladmin/eladmin-system/src/main/resources/mapper/CustomerPackageCategoryMapper.xml
git commit -m "feat: add customer package category backend"
```

## Task 3: Build customer profile aggregate backend with tests first

**Files:**
- Create: `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/CustomerProfileServiceImplTest.java`
- Create: `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/rest/CustomerProfileControllerTest.java`
- Create: `eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/integration/CustomerProfileIntegrationTest.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerProfile.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerProfileAddress.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerProfilePackage.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileQueryCriteria.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileSaveDto.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileDetailDto.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileStatusUpdateDto.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileStatusRequestDto.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerProfileMapper.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerProfileAddressMapper.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerProfilePackageMapper.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/CustomerProfileService.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/impl/CustomerProfileServiceImpl.java`
- Create: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/rest/CustomerProfileController.java`
- Create: `eladmin/eladmin-system/src/main/resources/mapper/CustomerProfileMapper.xml`
- Reference: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/meal/service/impl/CustomerDietaryRestrictionsServiceImpl.java`

- [ ] **Step 1: Write the failing service tests for the aggregate rules**

```java
@Test
void shouldComputeTotalCountWhenOnlyBreakfastPresent() {
    CustomerProfileSaveDto dto = validDto();
    dto.getPackageInfo().setBreakfastCount(10);
    dto.getPackageInfo().setLunchDinnerCount(null);

    service.normalizeAndValidate(dto);

    assertEquals(10, dto.getPackageInfo().getTotalCount());
}

@Test
void shouldRejectWhenAllAddressSlotsAreEmpty() {
    CustomerProfileSaveDto dto = validDto();
    dto.setAddresses(List.of());

    assertThrows(BadRequestException.class, () -> service.normalizeAndValidate(dto));
}

@Test
void shouldRejectEnableRequestWithoutPackageUpdatePayload() {
    CustomerProfileStatusRequestDto dto = new CustomerProfileStatusRequestDto();
    dto.setStatus(true);
    dto.setPackageInfo(null);

    assertThrows(BadRequestException.class, () -> service.updateStatus(1L, dto));
}

@Test
void shouldNormalizeAddressOrderAsDefaultWorkdayWeekend() {
    CustomerProfileSaveDto dto = validDto();
    dto.setAddresses(List.of(
        address("WEEKEND", "周末地址"),
        address("DEFAULT", "默认地址")
    ));

    service.normalizeAndValidate(dto);

    assertEquals("DEFAULT", dto.getAddresses().get(0).getAddressType());
    assertEquals("WEEKEND", dto.getAddresses().get(1).getAddressType());
}
```

- [ ] **Step 2: Write the failing controller contract test**

```java
@Test
void shouldCreateCustomerProfile() throws Exception {
    String body = """
      {
        \"customerCode\": \"A001\",
        \"customerName\": \"张三\",
        \"phone\": \"13800000000\",
        \"addresses\": [{\"addressType\":\"DEFAULT\",\"addressDetail\":\"地址1\"}],
        \"packageInfo\": {
          \"parentPackageId\": 1,
          \"childPackageId\": 11,
          \"breakfastCount\": 10,
          \"lunchDinnerCount\": 20,
          \"startDate\": \"2026-03-25\",
          \"endDate\": \"2026-04-25\"
        }
      }
    """;

    mockMvc.perform(post("/api/customerProfile").contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isCreated());
}

@Test
void shouldRejectDeleteEndpointExposure() throws Exception {
    mockMvc.perform(delete("/api/customerProfile/1"))
      .andExpect(status().is4xxClientError());
}

@Test
void shouldRequirePackagePayloadWhenReEnablingProfile() throws Exception {
    String body = """
      {
        \"status\": true,
        \"packageInfo\": null
      }
    """;

    mockMvc.perform(put("/api/customerProfile/1/status").contentType(MediaType.APPLICATION_JSON).content(body))
      .andExpect(status().isBadRequest());
}
```

- [ ] **Step 3: Run the tests and confirm RED**

Run:

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -pl eladmin-system -DskipTests=false -Dtest=CustomerProfileServiceImplTest,CustomerProfileControllerTest test
```

Expected: FAIL because the profile aggregate backend does not exist yet.

- [ ] **Step 4: Implement the minimal aggregate backend**

Core DTO shape:

```java
@Data
public class CustomerProfileSaveDto {
    private Long id;
    private String customerCode;
    private String customerName;
    private String phone;
    private Integer gestationalWeek;
    private List<String> allergyTags;
    private String medicalRequirements;
    private Boolean status;
    private List<AddressDto> addresses;
    private PackageInfoDto packageInfo;
}

@Data
public class CustomerProfileStatusRequestDto {
    private Boolean status;
    private PackageInfoDto packageInfo;
}
```

Implement service rules:
- `DEFAULT/WORKDAY/WEEKEND` at least one non-empty row
- normalize address row order to `DEFAULT -> WORKDAY -> WEEKEND`
- no duplicate address types
- `gestationalWeek` must be a positive integer when present
- allergy tags must be trimmed, deduplicated, and normalized before save
- `totalCount = breakfast + lunchDinner`
- breakfast/lunchDinner cannot both be null
- child category must belong to selected parent
- generate-code endpoint must reject missing/disabled/no-prefix parent cases
- if profile status is disabled, package `active_flag` must be `0`
- if re-enabling a disabled profile, request must include a full valid `packageInfo` payload; do not restore old package state automatically
- no public delete profile endpoint
- full overwrite update of addresses and package record

- [ ] **Step 5: Add integration tests for aggregate transaction and status linkage**

Create `CustomerProfileIntegrationTest` covering:
- create profile with one address and verify address + package rows persisted
- update profile with a different address slot set and verify full overwrite semantics
- disable profile and verify package `active_flag = 0`
- re-enable profile without `packageInfo` and expect rejection

Run:

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -pl eladmin-system -DskipTests=false -Dtest=CustomerProfileIntegrationTest test
```

Expected: PASS.

- [ ] **Step 6: Run the targeted tests until GREEN**

Run the same Maven command from Step 3 plus the integration test:

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -pl eladmin-system -DskipTests=false -Dtest=CustomerProfileServiceImplTest,CustomerProfileControllerTest,CustomerProfileIntegrationTest test
```

Expected: PASS.

- [ ] **Step 7: Run a backend smoke query for mapper XML correctness**

Run:

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -pl eladmin-system -DskipTests=false -Dtest=CustomerProfileServiceImplTest test
```

Expected: PASS without MyBatis mapping exceptions.

- [ ] **Step 8: Commit**

```bash
git add \
  eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/service/CustomerProfileServiceImplTest.java \
  eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/rest/CustomerProfileControllerTest.java \
  eladmin/eladmin-system/src/test/java/me/zhengjie/modules/customer/profile/integration/CustomerProfileIntegrationTest.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerProfile.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerProfileAddress.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/CustomerProfilePackage.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileQueryCriteria.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileSaveDto.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileDetailDto.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileStatusUpdateDto.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/domain/dto/CustomerProfileStatusRequestDto.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerProfileMapper.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerProfileAddressMapper.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/mapper/CustomerProfilePackageMapper.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/CustomerProfileService.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/service/impl/CustomerProfileServiceImpl.java \
  eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/rest/CustomerProfileController.java \
  eladmin/eladmin-system/src/main/resources/mapper/CustomerProfileMapper.xml
git commit -m "feat: add customer profile aggregate backend"
```

## Task 4: Finish backend documentation and permission naming

**Files:**
- Modify: `eladmin/doc/customer-profile-api.md`
- Modify: `eladmin/sql/customer-profile.sql`
- Reference: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/rest/CustomerProfileController.java`
- Reference: `eladmin/eladmin-system/src/main/java/me/zhengjie/modules/customer/profile/rest/CustomerPackageCategoryController.java`

- [ ] **Step 1: Add final endpoint paths and permission strings to the markdown doc**

Document these permissions explicitly:
- `customerProfile:list`
- `customerProfile:add`
- `customerProfile:edit`
- `customerProfile:status`
- `customerProfile:del` (reserved for internal tooling only; do not expose a normal user-facing delete endpoint)
- `customerPackageCategory:list`
- `customerPackageCategory:add`
- `customerPackageCategory:edit`
- `customerPackageCategory:del`

- [ ] **Step 2: Align SQL seed menu/button rows with the actual permission names**

Make sure component values match frontend page paths exactly:
- `customer/profile/index`
- `customer/packageCategory/index`

- [ ] **Step 3: Verify no amount fields leaked into contract or SQL**

Search checklist:
- no `deposit`
- no `amount`
- no `total_amount`
- no `paid_amount`

- [ ] **Step 4: Commit**

```bash
git add eladmin/doc/customer-profile-api.md eladmin/sql/customer-profile.sql
git commit -m "docs: finalize customer profile api and permission contract"
```

## Task 5: Build the package-category Vue tree page

**Files:**
- Create: `eladmin-web/src/api/customerPackageCategory.js`
- Create: `eladmin-web/src/views/customer/packageCategory/index.vue`
- Reference: `eladmin-web/src/views/system/dept/index.vue`
- Reference: `eladmin-web/src/api/system/dept.js`

- [ ] **Step 1: Sketch the frontend contract before coding**

API file should expose:

```js
export function getTree(params) {}
export function getParents() {}
export function add(data) {}
export function edit(data) {}
export function editStatus(id, enabled) {}
export function del(id) {}
```

Page needs:
- tree table
- dialog form
- parent selector via `vue-treeselect`
- enabled switch
- validation for single-letter uppercase `codePrefix` on parent rows

- [ ] **Step 2: Implement the API file and page component**

Reuse these UI patterns from `system/dept`:
- lazy tree loading if needed, otherwise eager tree render
- `CRUD` mixin for dialog lifecycle
- `el-switch` for enabled state
- tree parent selector in the dialog

- [ ] **Step 3: Add manual validation guards in the page**

Guard cases:
- level 1 parent requires `codePrefix`
- level 2 child cannot set `codePrefix`
- disabled parent should not be selectable as a new child parent

- [ ] **Step 4: Run lint / build verification**

Run:

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin-web && npm run lint
```

Expected: PASS with no new ESLint errors.

- [ ] **Step 5: Manual smoke-test in the browser**

Verify:
- create parent category
- create child category
- toggle enabled status
- delete blocked when category is referenced or has children

- [ ] **Step 6: Commit**

```bash
git add eladmin-web/src/api/customerPackageCategory.js eladmin-web/src/views/customer/packageCategory/index.vue
git commit -m "feat: add customer package category page"
```

## Task 6: Build the customer profile Vue CRUD page

**Files:**
- Create: `eladmin-web/src/api/customerProfile.js`
- Create: `eladmin-web/src/views/customer/profile/index.vue`
- Create: `eladmin-web/src/views/customer/profile/form.vue`
- Reference: `eladmin-web/src/views/meal/customerDietaryRestrictions/index.vue`

- [ ] **Step 1: Sketch the form model and list columns before coding**

Default form shape:

```js
const defaultForm = {
  id: null,
  customerCode: null,
  customerName: null,
  phone: null,
  gestationalWeek: null,
  allergyTags: [],
  medicalRequirements: null,
  status: true,
  addresses: [
    { addressType: 'DEFAULT', addressDetail: '' },
    { addressType: 'WORKDAY', addressDetail: '' },
    { addressType: 'WEEKEND', addressDetail: '' }
  ],
  packageInfo: {
    parentPackageId: null,
    childPackageId: null,
    breakfastCount: null,
    lunchDinnerCount: null,
    totalCount: 0,
    startDate: null,
    endDate: null
  }
}
```

- [ ] **Step 2: Implement the API file**

Expose:
- `getProfiles(params)`
- `getProfile(id)`
- `generateCode(parentPackageId)`
- `add(data)`
- `edit(data)`
- `updateStatus(id, payload)` where `payload` includes `status` and, when re-enabling, a full `packageInfo`

- [ ] **Step 3: Implement the list page**

List must show:
- code / name / phone
- default address
- parent package / child package
- breakfast count / lunch+dinner count / total count
- start/end date
- gestational week / status

Search fields:
- code / name / phone / parent package / child package / status

- [ ] **Step 4: Implement the dedicated form component**

Use these behaviors:
- selecting parent package loads child packages and triggers `generateCode`
- if user manually changed code, switching parent prompts before overwrite
- total count is computed in the component view as `Number(breakfastCount || 0) + Number(lunchDinnerCount || 0)`
- `allergyTags` uses `el-select` with `multiple + filterable + allow-create`
- validate that at least one of the three address textboxes is non-empty
- status toggle to enabled must open or reuse a package-info confirmation flow so the request can include a full valid `packageInfo` payload
- status toggle to disabled sends `status=false` and does not require package edits

- [ ] **Step 5: Add an E2E test skeleton for the key profile flow**

Create `eladmin-web/tests/e2e/customer-profile.spec.js` covering:
- create category child under enabled parent
- create profile with one address
- verify generated code appears
- disable profile
- attempt re-enable without package payload and verify rejection in UI or API response handling

If the repository has no runnable E2E harness yet, still create the spec file and document the exact command or missing harness note in the file header.

- [ ] **Step 6: Run lint and a manual UI smoke test**

Run:

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin-web && npm run lint
```

Manual smoke checklist:
- create profile with only default address
- create profile with only weekend address
- create profile with breakfast only
- create profile with lunch+dinner only
- edit profile and change parent package
- disable profile and verify status switch still shows the record

- [ ] **Step 7: Commit**

```bash
git add \
  eladmin-web/src/api/customerProfile.js \
  eladmin-web/src/views/customer/profile/index.vue \
  eladmin-web/src/views/customer/profile/form.vue \
  eladmin-web/tests/e2e/customer-profile.spec.js
git commit -m "feat: add customer profile page"
```

## Task 7: End-to-end verification, docs sync, and final cleanup

**Files:**
- Modify: `eladmin/doc/customer-profile-api.md`
- Modify: `eladmin/sql/customer-profile.sql`
- Reference: `docs/superpowers/specs/2026-03-25-customer-profile-design.md`
- Reference: `docs/superpowers/plans/2026-03-25-customer-profile-module.md`

- [ ] **Step 1: Run backend targeted tests together**

Run:

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin && mvn -pl eladmin-system -DskipTests=false -Dtest=CustomerPackageCategoryServiceImplTest,CustomerProfileServiceImplTest,CustomerProfileControllerTest test
```

Expected: PASS.

- [ ] **Step 2: Run frontend lint**

Run:

```bash
cd /Users/qqx/job/code/eladmin-mp/eladmin-web && npm run lint
```

Expected: PASS.

- [ ] **Step 3: Run a manual end-to-end smoke flow**

Flow:
1. import or execute `eladmin/sql/customer-profile.sql`
2. open package category page and confirm seed nodes exist
3. create a new child category under `月子餐`
4. create a new customer profile using that child category
5. confirm code auto-generated from parent prefix
6. confirm detail API returns addresses + packageInfo
7. confirm disabling profile flips package `active_flag` to false

- [ ] **Step 4: Update the API markdown with any endpoint or payload drift found during testing**

If controller request/response fields differ from the draft contract, update `eladmin/doc/customer-profile-api.md` immediately.

- [ ] **Step 5: Commit**

```bash
git add eladmin/doc/customer-profile-api.md eladmin/sql/customer-profile.sql
git commit -m "test: verify customer profile module end to end"
```

## Test strategy summary

### Backend automated tests
- `CustomerPackageCategoryServiceImplTest`
  - duplicate prefix validation
  - parent/child relation validation
  - delete blocking rules
- `CustomerProfileServiceImplTest`
  - address slot validation
  - count normalization
  - code generation
  - status/package flag coordination
- `CustomerProfileControllerTest`
  - create contract
  - detail payload shape
  - status endpoint contract

### Frontend manual verification
- package tree CRUD
- code generation interaction
- at-least-one-address validation
- total count display
- status switches

## Notes for the implementing engineer

- Follow the spec, not the old meal table shape.
- Do not add amount/deposit fields even if you see an easy place to put them.
- Keep API docs in `eladmin/doc/` in sync with every backend contract change.
- Prefer smaller Vue components: put the big profile dialog in `form.vue`, not inline in `index.vue`.
- If `modules/customer/profile` feels deeper than the rest of the codebase, keep it anyway for this feature because it matches the approved spec boundary.
