# gateway 协议表示 · v1

本文维护 gateway 签名、配置与快照的报文和编码表示。业务判定见 [DOMAIN](DOMAIN.md)，管理端点、HTTP 失败映射及跨服务协作契约见 [RESPONSIBILITIES](RESPONSIBILITIES.md)。专属适配实现见 [DESIGN](DESIGN.md)。

## 签名协议

凭据指定签名协议；选择、有效期、候选和防重放的业务规则见 [接入段](DOMAIN.md#42-接入段access)。下面只定义互操作计算。

| 协议 | 计算 |
| --- | --- |
| LEGACY_SECRET_PARAMS_TIMESTAMP_MD5_UPPER | MD5(secret + params + timestamp)，大写十六进制，保留既有 params 提取方式。 |
| LEGACY_SECRET_TIMESTAMP_BODY_SHA256 | SHA256(secret + timestamp + body)。 |
| LEGACY_SECRET_TIMESTAMP_BODY_HMAC_SHA256 | HMAC-SHA256(secret, timestamp + body)。 |
| HTTP_REQUEST_SIGNATURE_V1 | 以下规范字节的 HMAC-SHA256。 |

历史协议继续兼容既有计算，不因此接纳旧配置 schema。新接入使用 HTTP_REQUEST_SIGNATURE_V1；历史固定向量见 [签名兼容测试](../gateway-infrastructure-security-spring/src/test/java/com/cloud/gateway/infrastructure/security/spring/RequestSignatureVerifierTest.java)。

### HTTP_REQUEST_SIGNATURE_V1

输入 Header 为 `X-App-Id`、`X-Client-Id`、`X-Credential-Id`、`X-Timestamp`、`X-Nonce`、`X-Signature`。共享名称所有者见 [framework 契约](../../framework/docs/RESPONSIBILITIES.md#framework-core)；本协议规定这些输入的签名含义。

原文依次编码：协议名 `HTTP_REQUEST_SIGNATURE_V1` → method → rawPath → rawQuery → AppId → ClientId → credentialId → timestamp → nonce → 原始 body 的 SHA-256 小写十六进制摘要。

- 每个字段使用 UTF-8 **字节长度** netstring：`长度:值,`。缺失 query 为 `-1:,`，显式空 query 为 `0:,`。
- 原始路径/查询不解码、不排序、不改写、不修剪，不进行大小写或 Unicode 规范化。
- method 为 1–32 个合法 token 字符；请求事实中方法大写化。
- rawPath 以 `/` 开始；路径不含原始 `?`，路径/查询不含 `#` 或非可见 ASCII；百分号后必须有两位十六进制字符。
- AppId、ClientId、credentialId 非空白，最长 256，字符范围为可见 ASCII `0x21`–`0x7e`。
- timestamp 是规范十进制 epoch 毫秒，`0` 或无前导零的正数，最多 19 位。
- nonce 为 22–128 个无 padding 的 base64url 字符，调用方以至少 128 bit 随机数生成。
- secret 为密钥，上述字节为消息，签名为 64 字符小写十六进制 HMAC-SHA256。

对应固定向量见 [V1 协议测试](../gateway-infrastructure-security-spring/src/test/java/com/cloud/gateway/infrastructure/security/spring/signature/HttpRequestSignatureV1VerifierTest.java)。上述格式不更改请求改写前验证的安全要求。

### 证据大小与声明

签名请求以及 GET 请求的入口 body 证据采集上限为 1 MiB。端点要求签名或已提交 timestamp/signature 证据时采集原始 body；普通未签名非 GET 请求不由该采集器统一缓冲。超限的 HTTP 结果见职责文档。

APP_VERSION 声明为 1–64 个 ASCII 字母、数字或 `._+-`，不修剪。其信任含义见 [Header 领域规则](DOMAIN.md#6-header-与信任上下文)，不属于签名证明或授权身份。

## 主体证据

Bearer Token 对应 HOST_SESSION，`BROWSER_SESSION` Cookie 对应 BROWSER_SESSION。Host Token 可采用 JWT/JWK 或 Reference introspection；Subject 的业务约束与认证结局见 [主体规则](DOMAIN.md#27-跨模型规则)，user-auth 协作顺序见职责文档。

Host 认证数据使用 `sub`、`session_id`、`iss`、`exp`、`aud` 和 `scope`（或 `scp`）；JWT/introspection 的协议所有权在 user-auth。gateway 的 authenticationExpiresAt 从已认证 Token exp 恢复；Browser 路径以收到验证响应时刻加 remaining idle TTL 恢复该信息。此字段的含义和限制由 DOMAIN 定义，不是绝对会话期限。

## 配置表示

HTTP 使用 JSON；Nacos Definition 与 Snapshot 使用 YAML。管理载荷类型见 [gateway-api](../gateway-api/src/main/java/com/cloud/gateway/api/facade/admin)，存储载荷见 [存储协议类型](../gateway-infrastructure/src/main/java/com/cloud/gateway/infrastructure/policy)。这些类型提供字段定义，示例不取代协议约束。

- 能力组合包含 `bundles`、`bindings` 两个按字符串 ID 索引的对象，条目保留 `bundleId`、`bindingId`。来源 key 必须与还原身份一致；标识规范化后的冲突拒绝。
- 原始 JSON/YAML 的重复 key 在解析阶段拒绝，不能覆盖后再判重。旧 bindings 数组不兼容。
- Bundle 使用 `capabilities` 对象，以精确能力名为键；能力内部字段为 camelCase，Duration 为 ISO-8601。支持的节点为 `rate-limit`、`timeout`、`header-apply`，载荷见当前 API/存储类型。
- 本地 `gateway.access-definition.policies` 下 Bundle/Binding 的绑定字段使用 kebab-case；能力内部仍为 camelCase。只有本地 Properties 适配允许将数字索引对象还原为集合，HTTP/存储必须使用真实数组。
- 未知能力、未知嵌套字段、非对象能力节点拒绝；能力节点不能为 null，合法的嵌套 JSON null 不因此一律删除。配置不能指定任意 Java 类名。
- Client 配置以 `clients` 聚合，凭据位于 `credentials.activeCredential/graceCredential`；端点状态为 `status: ENABLED/DISABLED`，主体要求为 REQUIRED/ANONYMOUS，Client 要求为 REQUIRED/OPTIONAL。
- 旧分离凭据 Definition、`enabled` 布尔字段、固定能力字段及旧快照结构没有混合读取或别名兼容层。开发配置与 LKG 必须使用当前 schema。

重复身份、引用、合并及空集合含义由 [能力领域规则](DOMAIN.md#3-能力组合与绑定解析)定义；保存/发布分别采用的校验范围见 DOMAIN，不在此重复判定表。

### 管理能力组合载荷示例

端点与调用结果见 [管理契约](RESPONSIBILITIES.md#管理调用方)。expectedSourceRevision 须替换为最近读取或保存返回的令牌；初始化通过配置中心完成。

```json
{
  "expectedSourceRevision": "current-definition-revision",
  "policies": {
    "bundles": {
      "default": {
        "bundleId": "default",
        "capabilities": {
          "timeout": { "connect": "PT1S", "read": "PT3S", "total": "PT5S" }
        }
      }
    },
    "bindings": {
      "all": { "bindingId": "all", "bundleId": "default", "tags": [] }
    }
  }
}
```

管理载荷不能通过 body 中的 userId/sessionId 证明权限，可信管理上下文由认证入口提供。

## 快照与规范化编码

四份 Definition 为 `gateway-endpoints.yaml`、`gateway-app-capabilities.yaml`、`gateway-client-capabilities.yaml`、`gateway-access-configuration.yaml`；完整 Snapshot 为 `gateway-access.yaml`。部署 dataId/group 和 LKG 路径见 README。端点条目自带 `access`；凭据转发表示位于 `capabilities.header-apply.credentialForwarding`。

快照载荷携带 version、content、contentHash、publishedAt；源 revision 表示为不透明字符串，发布版本为正数整数。业务版本及有效性见 [快照规则](DOMAIN.md#7-快照与发布)。

contentHash 编码规范化业务内容，不包含版本及 publishedAt。Bundle/Binding 索引和能力节点按标识稳定排序；集合规范化、时间格式及空值省略由各节点编码契约确定。完整编码实现见 [Canonical 编码](../gateway-infrastructure/src/main/java/com/cloud/gateway/infrastructure/policy/DefaultCanonicalPolicyContentMapper.java)，样例与当前固定向量是互操作参考，摘要以规范化编码为准。

配置/快照 schema 与摘要格式关联；实例必须支持全部配置能力后才能发布对应内容，旧实例不能接纳未知能力。旧结构需重新生成，保留历史签名算法不等于保留旧 schema。完整示例见 [Nacos 示例目录](../gateway-infrastructure-persistence-nacos/src/main/resources/examples)。
