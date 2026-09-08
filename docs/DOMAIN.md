# gateway 领域设计

本文定义 `gateway` 的 **Ingress Access Control（入口访问控制）** 领域。目标形态由 `PolicySnapshotPublisher` 和 `PolicySnapshotProvider` 两个端口表达。

## 一、领域边界

gateway 把外部不可信请求转换为内部可信请求上下文，回答五个问题：

1. 请求声明的 ClientApp 是否已登记且可用？
2. 当前 Channel 是否允许该 ClientApp 使用？
3. Endpoint 是否要求调用方证明其持有当前 Channel 的有效凭据？
4. Endpoint 要求哪种 Subject 认证，以及哪些 scope、audience？
5. 验证成功后，可以向下游写入哪些可信事实？

| 能力 | 所有者 |
| --- | --- |
| ClientApp 的入口状态、平台和浏览器 Origin 白名单 | `IngressClientAppPolicy` |
| Channel 允许使用的 ClientApp 集合 | `IngressChannelAccessPolicy` |
| Channel 的请求签名凭据及其轮换生命周期 | `IngressChannelCredentialPolicy` |
| Endpoint 的 Channel Credential、Channel、Subject、scope、audience 和转发规则 | `IngressRouteAccessPolicy` |
| EndpointId 到 HTTP matcher 和逻辑 delivery route 的契约 | gateway `EndpointCatalog` |
| Access/Refresh Token 的签发与撤销 | `user-auth` 认证子域 |
| Role、Permission 和渠道功能授权来源 | `user-auth` 功能授权子域 |
| 业务资源归属和数据范围 | 拥有资源的业务领域 |
| JWT、introspection、配置中心、WebFlux、SCG 和 Redis | gateway infrastructure |

gateway 不读取业务 Role、Permission 或资源数据。

## 二、统一语言

| 术语 | 定义 |
| --- | --- |
| **ClientAppId** | 调用应用声明的稳定标识。gateway 用它解析受管理的 ClientApp Policy；一个 ClientAppId 唯一绑定一个 platform，但仅完成策略匹配不等于证明调用方独占该应用身份。 |
| **ChannelCredential** | 按 Channel 颁发、用于证明调用方持有该 Channel 调用资格的请求签名凭据。 |
| **ChannelCode** | 本次业务访问所属渠道，不是 OAuth2 scope。 |
| **EndpointId** | 与网关产品无关的稳定逻辑端点身份，例如 `USER_AUTH.LOGIN_MOBILE_OTP`。 |
| **EndpointCatalog** | EndpointId 与 HTTP matcher、逻辑 DeliveryRouteId 的权威映射。 |
| **AuthenticatedSubject** | 从已验证 Access Token 或 Browser Session 恢复出的主体事实。 |
| **IngressPolicySnapshot** | 同时包含 EndpointCatalog 和四类入口策略的一份完整、不可变入口配置。 |
| **TrustedRequestContext** | gateway 允许请求后产生的可信事实；HTTP `X-*` 只是传输表示。 |
| **DeclaredClientMetadata** | 客户端自行声明、只做格式和长度清洗的非可信请求元数据；当前仅包含可选的 clientVersion，不参与入口判定。 |
| **Protected Header** | 外部值必须删除，只能由 gateway 根据验证结果重写的 Header。 |
| **IngressAccessDecision** | Allow 或 Deny 的入口判定，不等价于业务操作最终授权。 |

### 错误码

`gateway` 属于 `EDGE` 命名空间，使用首个工程前缀 `800`。错误枚举只声明三位本地码，框架组合后的完整错误码为六位。

- `000-099`：共享领域错误；当前包含 `DOMAIN_ENTITY_ID_INVALID(800000)`、`DOMAIN_OBJECT_FIELD_REQUIRED(800001)`、`DOMAIN_OBJECT_FIELD_INVALID(800002)`、`DOMAIN_OBJECT_STATE_INVALID(800003)`，`004-099` 当前未分配。
- `100-149`：`IngressPolicySnapshot` 聚合；当前包含不存在、已存在和快照非法错误。
- `150-199`：`EndpointCatalog` 聚合；当前包含不存在、已存在和端点匹配冲突错误。
- `200-599`：领域聚合预留。
- `600-699`：应用错误，包括版本/CAS 冲突、无变化发布、Definition 变化、Publish/Rollback 锁和回退状态；已分配至 `APP_POLICY_DEFINITION_READ_ONLY(800612)`，其中只读错误码因读写端口拆分而仅作稳定性保留，不再用于正常装配路径。
- `700-799`：gateway 自有技术机制错误，包括策略序列化/反序列化、策略存储、last-known-good、rollbackState 和 secret 解析失败；当前使用至 `INFRA_TECH_POLICY_ROLLBACK_STATE_FAILED(800705)`。
- `800-899`：外部 ACL 错误，包括 Access Token、Browser Session 与 Nacos 提供方；当前新增 `INFRA_ACL_NACOS_PROVIDER_UNAVAILABLE(800802)`、`INFRA_ACL_NACOS_CONTENT_INVALID(800803)`。

当前工程处于开发阶段，但错误码仍按发布后不可改写、不可复用的约束追加维护。

## 三、Endpoint 契约

`IngressRouteAccessPolicy` 以 EndpointId 为身份，因此 Endpoint 解析是所有策略判断的前置契约。

```text
EndpointDefinition
  endpointId
  ownerService
  matchers
    - httpMethod
      pathPattern
  deliveryRouteId
```

`DeliveryRouteId` 是 gateway 的逻辑交付路由身份，infrastructure 将它映射为 SCG routeId；领域模型不依赖 SCG。最终装配出的一个完整 `EndpointDefinition`。

| 字段 | 维护者 | 说明 |
| --- | --- | --- |
| `endpointId` | 下游服务 | 稳定业务操作身份，不随网关或 path 的技术迁移随意改变。 |
| `ownerService` | 下游服务清单 | 标识接口契约的所有者。 |
| `matchers` | 下游服务 | 属于其公开 HTTP 契约，必须与 Controller/OpenAPI 一致。 |
| `deliveryRouteId` | gateway | 逻辑交付路由；下游服务不感知 SCG routeId。 |

## 四、核心领域模型

### 1. 入口策略

```text
# CLIENT_APP
IngressClientAppPolicy
  clientAppId
  status: ENABLED | DISABLED
  platform
  allowedBrowserOrigins

# CHANNEL
IngressChannelAccessPolicy
  channelCode
  allowedClientAppIds

# CHANNEL_CREDENTIAL
IngressChannelCredentialPolicy
  channelCode
  activeCredential: RequestSignatureChannelCredential
  graceCredential: RequestSignatureChannelCredential?

# ROUTE
IngressRouteAccessPolicy
  endpointId
  channelCredentialRequirement
  channelRequirement
  subjectRequirement
  requiredScopes
  requiredAudiences
  credentialForwardingPolicy
  enabled
```

`IngressChannelCredentialPolicy` 以 `channelCode` 为身份，与 `IngressChannelAccessPolicy` 相互独立维护、统一随完整快照发布：前者回答"这个渠道用什么凭据验签"，后者回答"哪些 ClientApp 允许使用这个渠道"，两者通过 `channelCode` 关联。一个渠道的密钥轮换不需要修改该渠道的 ClientApp 访问集合，但发布时仍对四类策略执行统一 CAS 和交叉校验。

`channelCode` 的渠道目录由渠道/网关边界管理；gateway 只维护 `appId + channelCode` 合法组合（IngressChannelAccessPolicy）与渠道验签凭据（IngressChannelCredentialPolicy），不拥有渠道主数据。同一个 ClientApp 可以进入多个渠道，同一个渠道也可以由多个 ClientApp 进入。

规则：

- ClientAppId、ChannelCode、EndpointId 不可原地改名；修改身份等同于删除旧项并创建新项。
- ClientAppId、ChannelCode、EndpointId 和 ChannelCredentialId 在 domain 内使用继承 `EntityId` 的强类型；API、配置和 Header 仅保留其字符串传输表示。
- 一个 ClientAppId 唯一绑定一个 platform，platform 不接受外部 Header 指定，也不能原地迁移；不同平台注册不同 ClientAppId。ClientApp 被停用或替代时登记新的 appId，platform 不在原 appId 上迁移；下游 `user-auth` 的 `LoginSession.Client` 保存解析后的 appId/platform/version 历史快照，因此替换不影响历史会话表达当时的接入形态。
- `allowedBrowserOrigins` 是该 ClientApp 可以发起 Browser Session Cookie 请求的完整 Origin 集合，只接受 `http/https + host + 可选 port`，不接受 path、query、fragment、userinfo 或通配符；配置按规范化后的完整 Origin 精确匹配。
- `BROWSER_SESSION_REQUIRED` Endpoint 的非安全方法必须携带命中当前 ClientApp 白名单的 `Origin`；缺失、格式非法、白名单为空或不匹配都拒绝。`GET/HEAD/OPTIONS/TRACE` 不要求 Origin，但不得产生状态变更。
- DISABLED ClientApp 不能产生 Allow，包括匿名登录入口。
- 未配置的 ClientApp、Channel 组合或 Endpoint 默认拒绝。
- 每个请求必须先解析出一个已登记且 ENABLED 的 ClientAppId，这是所有策略判断的共同前置事实。
- `IngressChannelAccessPolicy.allowedClientAppIds` 中的每个 ClientAppId 必须存在；引用 DISABLED ClientApp 可以保留配置，但运行时仍拒绝。
- `IngressChannelCredentialPolicy.activeCredential` 必须存在，`graceCredential` 最多一份；两份凭据的 channelCode 必须与策略身份一致，credentialId 在完整快照内唯一。
- Channel Credential 可以在开放 ClientApp 访问前预先配置；没有对应 Channel Access 授权时，即使验签成功也不能产生 Allow。
- 每个启用的 Route Policy 必须引用 EndpointCatalog 中存在的 EndpointId；四类策略与 EndpointCatalog 的上述关系在发布完整快照时统一校验。

### 2. Route 策略取值域

#### ChannelCredentialRequirement

| 值 | 语义 |
| --- | --- |
| `IGNORE` | 不要求额外的 Channel 签名认证；仍需满足"ClientApp 已登记且 ENABLED"这一共同前提。适用于 public client、匿名登录或其他允许公开调用的 Endpoint。 |
| `REQUEST_SIGNATURE` | 必须使用当前 Channel 的有效 `RequestSignatureChannelCredential` 验签。 |

`IGNORE` 不表示调用方已完成强身份认证。需要证明合作方持有 Channel 凭据的 Endpoint 必须使用 `REQUEST_SIGNATURE`。

#### ChannelRequirement

| 值 | 语义 |
| --- | --- |
| `REQUIRED` | 必须提交 channelCode，并通过 ClientApp + Channel 组合校验。 |
| `OPTIONAL` | 提交时必须校验；未提交时不产生 ChannelContext。 |
| `FORBIDDEN` | 不接受 channelCode；若请求携带任意 channelCode，直接产生 `IngressAccessDecision.Denied(reason=CHANNEL_NOT_PERMITTED_FOR_ENDPOINT)`，不静默丢弃。该拒绝原因区别于 `CHANNEL_NOT_ALLOWED`（ClientApp+Channel 组合未配置），用于区分"配置未匹配"与"端点本不该出现渠道语义"两类信号。 |

约束：

- `channelCredentialRequirement=REQUEST_SIGNATURE` 必须同时配置 `channelRequirement=REQUIRED`（当前 Request Signature Credential 与 Channel 绑定）。
- `channelRequirement=FORBIDDEN` 只能配置 `channelCredentialRequirement=IGNORE`。

#### SubjectRequirement

| 值 | 语义 |
| --- | --- |
| `IGNORE` | 网关不对本次请求执行 Subject 验证，也不产出 AuthenticatedSubject。包含真正匿名的业务端点，也包含 OAuth2/OIDC 协议端点（Subject 认证由 SAS 完成，网关不解析/不校验 Bearer Token）——这两类场景在网关执行路径上是同一件事：网关都不处理这层凭据。 |
| `HOST_SESSION_REQUIRED` | 必须携带有效 OAuth2 Bearer Access Token（本地验签或远程 introspection，domain 层等价），恢复 `subjectType=HOST_SESSION`。 |
| `HOST_SESSION_OPTIONAL` | 未携带 Token 时按匿名放行；一旦携带 Token，必须按与 `HOST_SESSION_REQUIRED` 完全相同的严格度验证（签名、过期、aud），验证失败直接 Deny（`SUBJECT_AUTHENTICATION_FAILED`），不得静默降级为匿名。"可选"仅体现在"未携带时不拒绝"，不体现在验证标准的放宽。 |
| `BROWSER_SESSION_REQUIRED` | 要求有效 Browser Session（HttpOnly Cookie），恢复 `subjectType=BROWSER_SESSION`。 |

约束：

- `HOST_SESSION_REQUIRED` 与 `HOST_SESSION_OPTIONAL`（Token 存在时）：`requiredAudiences` 必须非空，`requiredScopes` 按 Endpoint 需要配置。
- `BROWSER_SESSION_REQUIRED`：不产生 OAuth2 scope 或 audience，`requiredScopes`/`requiredAudiences` 必须为空，也不能用于管理端点。
- `IGNORE`：`requiredScopes` 和 `requiredAudiences` 必须为空。
- 管理端点要求 `HOST_SESSION_REQUIRED`、`admin` scope 和明确的 gateway audience。

#### CredentialForwardingPolicy

```text
CredentialForwardingPolicy
  authorizationHeader: STRIP | PRESERVE
  cookies: STRIP_ALL | ALLOW_LIST
  allowedCookieNames
```

Authorization 与 Cookie 独立控制，且不再从 `SubjectRequirement` 或其他字段派生：

- 普通业务 Endpoint 默认 `authorizationHeader=STRIP`、`cookies=STRIP_ALL`。
- OAuth2/OIDC 协议端点（`subjectRequirement=IGNORE` 且承担协议转发职责）需要保留 Authorization 时，显式配置 `authorizationHeader=PRESERVE`；默认仍删除全部 Cookie。
- Browser Session Cookie 在 gateway 完成验证后默认删除，不向普通下游传播。
- 只有明确可信的下游协议需要某个 Cookie 时才使用 `ALLOW_LIST`；禁止"全部 Cookie 透传"。

### 3. ChannelCredential

`ChannelCredential` 是具有生命周期的请求签名凭据实体，归属于 `IngressChannelCredentialPolicy`（按 `channelCode` 颁发，非按 `ClientAppId` 颁发）：

```text
RequestSignatureChannelCredential
  credentialId
  validFrom
  expiresAt
  channelCode
  signaturePayloadContract: LEGACY_SECRET_PARAMS_TIMESTAMP_MD5_UPPER
                           | LEGACY_SECRET_TIMESTAMP_BODY_SHA256
                           | SECRET_TIMESTAMP_BODY_HMAC_SHA256
  secretRef
```

三种 `signaturePayloadContract`：

- `LEGACY_SECRET_PARAMS_TIMESTAMP_MD5_UPPER`：`sign = MD5(secret + params + timestamp)`转大写，`params`为DES加密密文。
- `LEGACY_SECRET_TIMESTAMP_BODY_SHA256`：`sign = SHA256(secret + timestamp + body)`。
- `SECRET_TIMESTAMP_BODY_HMAC_SHA256`：`sign = HMAC-SHA256(key=secret, message=timestamp + body)`，新契约。

Request Signature 规则：

- 一个 `ChannelCode` 对应一个 `signaturePayloadContract`。同一 `ChannelCode` 不允许新旧契约凭据同时启用。
- `activeCredential` 是当前主凭据；`graceCredential` 仅用于密钥轮换期间短暂兼容上一份凭据。两者的角色由其在 Policy 中的位置表达，不再在凭据上重复维护 status。
- 发布新快照时，`activeCredential` 必须已经生效且尚未到期；运行时若其不再满足 `[validFrom, expiresAt)`，请求按凭据无效 fail-closed。`graceCredential` 只有在请求时仍落在自身 `[validFrom, expiresAt)` 区间内才成为候选，过期后直接忽略。由于协议不携带凭据选择标识，验签最多按确定顺序尝试两次：先 `activeCredential`，失败后再尝试有效的 `graceCredential`；禁止遍历历史凭据。
- 轮换时发布新快照：新凭据成为 `activeCredential`，原主凭据成为具有明确 `expiresAt` 的 `graceCredential`。宽限期结束后再次发布快照删除 `graceCredential`。撤销主凭据时必须通过新快照删除或替换它；已撤销、已过期和历史凭据不进入运行时快照，由发布历史和安全审计保存事实。
- 如需在未来时刻自动启用下一份凭据，由控制面定时发布新的完整快照；不在运行时 Policy 中同时保存历史、当前和待生效三类凭据。
- **共享 Channel 密钥**：secret 按 `ChannelCode` 颁发给外部，多个 `ClientAppId` 可共用同一 `ChannelCode` 的密钥，"哪些 ClientApp 允许使用这个渠道"由 `IngressChannelAccessPolicy` 单独管控，不依赖密钥的唯一性。
- Channel Credential 只证明请求方持有该 Channel 的凭据，不证明其独占某个 ClientAppId。持有同一 Channel secret 的调用方可以在该 Channel 的 `allowedClientAppIds` 中选择任一 ClientAppId，因此同一 Channel 下只能放置属于同一信任主体、允许共享该能力边界的 ClientApp；否则必须拆分 Channel 或将凭据模型提升为更细的主体维度。
- 两种 `LEGACY_*` 契约只用于存量兼容，不允许新接入继续选用；新增接入统一使用 `SECRET_TIMESTAMP_BODY_HMAC_SHA256`。验签结果必须使用恒定时间比较，secret 明文不得进入快照、日志或审计，只能通过 `secretRef` 在安全存储中解析。

验签成功后产生：

```text
ChannelCredentialAuthenticationMethod
  REQUEST_SIGNATURE

VerifiedChannelCredential
  authenticationMethod: ChannelCredentialAuthenticationMethod
  credentialId
  channelCode
  verifiedAt
```

`VerifiedChannelCredential` 只在 `channelCredentialRequirement=REQUEST_SIGNATURE` 且验签成功后产生；`channelCredentialRequirement=IGNORE` 场景下不产生该对象。`channelCode` 和 `credentialId` 来自实际命中的 `RequestSignatureChannelCredential`，不接受请求方自行声明。

Channel Credential 认证结果显式区分三种状态：`Absent` 表示路由未要求凭据或要求凭据但请求没有提交完整签名 Header，`Authenticated(credential)` 表示认证和防重放登记均成功，`Rejected` 表示时间戳非法、签名不匹配、凭据无效或 replay 登记失败。要求签名的 Route 将 `Absent` 映射为 `CHANNEL_CREDENTIAL_REQUIRED`，将 `Rejected` 映射为 `CHANNEL_CREDENTIAL_INVALID`。

`RequestSignatureChannelCredentialAuthenticator` 只编排时间窗口、active/grace 候选顺序、secret 解析和 replay 登记。每种 `SignaturePayloadContract` 由独立 `RequestSignatureVerifier` 实现；所有 verifier 在启动时注册到不可变 `RequestSignatureVerifierRegistry`。重复注册或遗漏任一枚举值必须使启动失败，运行时按凭据的 `signaturePayloadContract` 精确选择 verifier，所有实现统一使用恒定时间比较。

`ChannelCredentialAuthenticationMethod` 只描述 Channel Credential 的验证机制。当前取值域只有 `REQUEST_SIGNATURE`；它不描述 Subject 的认证来源。保留枚举是为了以后增加其他 Channel Credential 验证机制时不改变 `VerifiedChannelCredential` 的结构。

### 4. AuthenticatedSubject

```text
AuthenticatedSubject
  subjectType: HOST_SESSION | BROWSER_SESSION
  subjectId
  loginSessionId
  issuer（HOST_SESSION 时存在）
  audiences
  scopes
  credentialExpiresAt
```

Subject 认证结果显式区分四种状态：`Absent` 表示没有提交该路由要求的 Subject 凭据，
`Authenticated(subject)` 表示认证成功并产生可信 Subject，`Rejected` 表示调用方提交了凭据但认证失败，
`Unavailable(reason)` 表示认证依赖不可达或其响应协议非法。
`HOST_SESSION_OPTIONAL` 只允许 `Absent` 匿名通过；`Rejected` 必须以
`SUBJECT_AUTHENTICATION_FAILED` 拒绝，不能静默降级为匿名。

`SubjectAuthenticationOutcome` 只表达领域认证结局，不携带 Redis TTL 是否已延长等会话存储细节。
Browser Session 的在线验证与 idle TTL 滑动续期均由 user-auth 在一次原子操作中完成；gateway 只消费
验证结局和当前有效期边界，不接收“本次是否续期”标记，也不刷新或重写 Browser Session Cookie。

Spring Security adapter 对 JWT 和 Reference Token 执行等价校验：签名或 active 状态、允许算法、issuer、时间声明以及必需 claims；interfaces 的 `HostSessionSubjectAuthenticator` 再将已认证的 `Authentication` 统一归一化为 `subjectType=HOST_SESSION` 的 AuthenticatedSubject。IngressAccessPolicyEvaluator 按下述明确的子集关系校验 Route Policy 的 requiredAudiences/requiredScopes，防止面向其他资源服务器的 Token 被挪用。

JWT 本地验签与 Reference Token introspection 是 authorization server 和 gateway infrastructure 的部署、适配策略，不是入口访问控制领域概念。领域层、TrustedRequestContext 和下游 Header 都不携带 Token 格式；如果安全审计需要记录实际验证路径，只能作为 gateway 内部技术审计元数据，例如 `accessTokenFormat=SELF_CONTAINED | REFERENCE`，不得参与领域判定。

Audience 与 scope 均采用明确的 **AND/子集关系**：必须满足 `route.requiredAudiences ⊆ subject.audiences` 且 `route.requiredScopes ⊆ subject.scopes`，即 Route 声明的每一个 required 值都必须出现在 Subject 中。若未来存在新旧 audience 迁移期的“满足任意一个即可”需求，应另行增加显式的 audience alternative/set 模型，不能把当前 `requiredAudiences` 暗改成集合相交即可。

`credentialExpiresAt` 表示在本次认证完成时，该 Subject 凭据最早失效的时间边界。对 Access Token，它取 Token 的 `exp`；对 Browser Session，它取当前 idle expiry、absolute expiry 和父 LoginSession expiry 三者中的最早值。Browser Session 后续滑动只会影响未来请求重新认证时计算出的值，不能突破 absolute expiry 或父 LoginSession expiry。

Browser Session 由 gateway 在线验证状态、idle/absolute TTL 和父 LoginSession，产生 BROWSER_SESSION Subject；其 issuer、audiences、scopes 为空。它不能被提升为 HOST_SESSION。

### 5. 单一发布快照

四类入口策略共享同一发布生命周期和一个业务版本。`IngressChannelCredentialPolicy` 与另外三类策略一样进入完整快照，享受同一套 CAS、全量交叉校验、历史回退和运行时原子切换机制；它不是独立发布资源。

```text
IngressPolicySnapshot
  snapshotVersion（只递增）
  endpointCatalog
  clientAppPolicies
  channelAccessPolicies
  channelCredentialPolicies
  routeAccessPolicies
  contentHash
  publishedAt
```

- Admin 可以只修改其中一类或一条策略，但每次发布都基于上一完整快照构造候选，装入当前受版本控制的 EndpointCatalog，执行全量交叉校验后发布完整快照。
- snapshotVersion 是唯一业务版本；只有规范化业务内容发生变化才发布下一版本。
- `contentHash` 只对 `endpointCatalog`、`clientAppPolicies`、`channelAccessPolicies`、`channelCredentialPolicies`、`routeAccessPolicies` 五部分的规范化内容计算，明确排除 `snapshotVersion`、`contentHash`、`publishedAt`。规范化规则固定字段表示、集合排序、空值表示、字符编码和序列化格式，确保相同业务内容得到相同 hash。
- 回退把历史内容重新发布为更大的 snapshotVersion，不复用旧版本号。
- 一个请求只读取一次当前快照；请求执行期间不得混用版本。

完整快照发布是有意的取舍：用发布、交叉校验和运行时读取模型的整体简单性，换取单次发布的校验、序列化和分发成本与策略总量成正比。当前策略规模下接受该成本，并监控快照大小、发布耗时和实例加载耗时；只有超过明确容量阈值后，才评估分区快照或增量分发，不能提前牺牲跨策略原子一致性。

配置通知不会使所有 gateway 实例在同一时刻切换。每个实例必须先完整校验新快照，再原子替换本机不可变引用；同一请求始终只使用一个版本。滚动传播期间，不同实例短暂使用相邻 snapshotVersion 是当前明确接受的运行时一致性窗口。对必须全局立即生效的高危撤销，不能仅依赖配置传播，后续应另行设计实例确认、流量摘除或在线撤销机制。

### 6. TrustedRequestContext 与 Decision

```text
TrustedRequestContext
  clientContext(clientAppId, platform)
  channelContext(channelCode)?
  channelCredentialContext(credentialId, channelCode)?
  subjectContext(subjectType, subjectId, loginSessionId, audiences, scopes,
                 credentialExpiresAt)?
  policySnapshotVersion

IngressAccessDecision
  Allowed(trustedRequestContext)
  Denied(reason, policySnapshotVersion)
```

ClientAppId 必须存在；platform 只来自 ClientApp Policy。这里的 clientContext 表示 gateway 已确认该声明对应一份 ENABLED Policy，不把它提升为独立的调用方身份证明。Channel 只在策略允许并验证后存在。subjectId、loginSessionId 和 subjectType 必须一起存在或一起不存在。Deny 不携带可被下游误用的 TrustedRequestContext。

`TrustedRequestContext` 只保存由策略或认证结果建立的事实。客户端自行上报的 `clientVersion` 即使经过格式、长度清洗，也仍是未经认证的声明，因此不属于该上下文。interfaces 层可将其建模为独立的 `DeclaredClientMetadata(clientVersion?)`，按转发契约单独写入 `X-Client-Version`；任何下游服务不得以该值作认证、授权或安全策略依据。

`channelCredentialContext` 是 `VerifiedChannelCredential` 面向下游的最小投影，只透传凭据身份及已验证的渠道绑定。`authenticationMethod` 和 `verifiedAt` 属于 gateway 安全审计细节，当前不向下游传播；新增传播字段必须先形成明确的下游用例，不能把完整认证对象直接暴露出去。

`subjectContext` 保留 `AuthenticatedSubject` 的统一术语和 `credentialExpiresAt`，避免派生短期凭据时越过上游凭据的有效边界。该字段默认只供 gateway 内部决策和派生能力使用，不自动映射为受保护 Header；需要下游消费时必须新增专门、可审计的传输契约。

`Allowed` 的快照版本只存在于 `trustedRequestContext.policySnapshotVersion`，避免同一事实出现两份可能不一致的数据；`Denied` 因没有可信上下文而独立携带判定所用的 snapshotVersion。

## 五、领域服务与请求判定

`IngressAccessPolicyEvaluator` 是纯领域服务：

```text
evaluate(
  clientAppPolicy,
  channelAccessPolicy,
  routeAccessPolicy,
  channelCredentialAuthenticationOutcome,
  requestedChannelCode?,
  subjectAuthenticationOutcome
) -> IngressAccessDecision
```

它校验 ClientApp 状态、Browser Session 非安全请求的 Origin、ChannelCredentialRequirement、ChannelRequirement、Channel Credential 认证结果及已验证凭据的渠道绑定、SubjectRequirement、requiredAudiences 和 requiredScopes。它不解析 HTTP、不验签、不调用 introspection、不操作 Redis，也不查询业务权限。

请求用例按以下顺序执行：

1. 从 `PolicySnapshotProvider` 读取一次当前完整快照。
2. 使用该快照内的 EndpointCatalog 根据 method/path 解析唯一 EndpointId，并从同一快照取得四类入口策略。
3. `channelCredentialRequirement=REQUEST_SIGNATURE` 时，interfaces 先区分缺失签名 Header，再使用 `IngressChannelCredentialPolicy` 调用 ChannelCredentialAuthenticator；Authenticator 通过 `RequestSignatureVerifierRegistry` 按 `signaturePayloadContract` 选择 verifier，并在验签成功后登记 replay evidence。ClientApp 只由 ClientApp Policy 和 Channel Access Policy 校验，不从 Channel Credential 推导。
4. interfaces 从请求中提取 HTTP 方法与原始 `Origin`，并按 SubjectRequirement 选择 `SubjectAuthenticator<T>`：Host Session 将 Spring Security
   `Authentication` 归一化为 Subject 认证结果；Browser Session 将 Cookie value 交给
   `BrowserSessionVerifier` 在线验证。验证结局显式区分 `Verified`、`Invalid` 和 `Unavailable`：`Verified`
   只携带已认证主体；user-auth 在验证过程中自行管理 Redis idle TTL 的按需滑动续期，不向 gateway 暴露
   存储写入结果。凭据无效映射为认证拒绝；验证依赖不可达与响应协议非法映射为不同的不可用原因，均不得建立 Subject。
   `HOST_SESSION_OPTIONAL` 仅在没有 Authentication 时产生 `Absent`，Authentication 存在但认证失败时产生 `Rejected`。
5. 将 Caller、Subject 认证结果以及 HTTP 方法、原始 `Origin` 一起交给 IngressAccessPolicyEvaluator。`subjectRequirement=BROWSER_SESSION_REQUIRED` 且 HTTP 方法不安全时，Evaluator 使用已匹配的 `IngressClientAppPolicy.allowedBrowserOrigins` 精确校验 `Origin`；认证适配器可能已并行完成技术验证，但 Origin 校验失败仍不得产生可信上下文。
6. Allow 时形成 TrustedRequestContext，执行流量策略并写入受保护 Header；interfaces 层可把已清洗但不可信的 DeclaredClientMetadata 独立转发；Deny 时停止转发。
7. 记录不含凭据明文的安全审计。

## 六、Admin 发布与快照分发

Admin API 是策略发布控制面；运行时 gateway 是只读消费方。Definition 是独立编辑态，EndpointCatalog 和四类入口策略分别存储；保存某类策略只修改对应 Definition，不生成 Snapshot。Nacos UI、Admin 或其他配置中心入口都可以编辑 Definition，显式 Publish 才把当前全部 Definition 组装成不可变 Snapshot。

```text
PolicySnapshotCommandService.publish
  1. 获取 publishLock，并确认 rollbackState 不存在
  2. 读取 EndpointCatalog 和四类策略 Definition，记录各自 sourceRevision
  3. 组装完整内容，执行交叉校验并计算 contentHash
  4. contentHash 与当前 Snapshot 相同则以 NO_CHANGES 失败，不增加版本
  5. 再次读取全部 Definition，任一 sourceRevision 变化则拒绝混合发布
  6. 生成 snapshotVersion + 1 和 publishedAt
  7. 通过 PolicySnapshotPublisher.compareAndSet(snapshotSourceRevision, snapshot) 发布
```

Definition 保存分别由 `ClientAppPolicyCommandService`、`ChannelAccessPolicyCommandService`、
`ChannelCredentialPolicyCommandService` 和 `RouteAccessPolicyCommandService` 承担；每个服务只依赖自己的
Definition Repository。`PolicySnapshotCommandService` 负责 Publish 和 Rollback。`PolicyPublishLock` 与
`PolicyRollbackLock` 分别固定锁 scene、资源 key 和获取失败错误，不由业务服务动态传入锁类型或错误码。

回退命令只携带 `targetSnapshotVersion`。执行前幂等创建永久 `rollbackState`，再按固定顺序获取 `rollbackLock`、`publishLock`。应用层从 History 读取目标 Snapshot，将其内容恢复到全部 Definition，校验 Definition 与目标一致后，必要时以当前版本加一发布 Snapshot；Definition 和当前 Snapshot 都与目标内容一致后才清除 `rollbackState`。失败时状态保留，后续可以使用相同或不同目标版本重新执行；已经完成的 Definition 和 Snapshot 写入会被跳过。普通 Publish 和 Definition Admin 保存只要发现 `rollbackState` 就拒绝执行。

`publishLock` 与 `rollbackLock` 是具有有限租约的 Redis 执行锁；`rollbackState` 是无 TTL 的持久状态，不承担互斥职责。Redis 锁不能阻止 Nacos UI 修改 Definition，因此 Publish 仍以 Definition sourceRevision 二次校验和 Snapshot CAS 作为独立正确性边界。

`sourceRevision` 是 Definition 和 Snapshot 端口用于乐观并发控制的技术版本，不进入领域模型；不同 adapter 可以使用配置 MD5、ETag、数据库 lockVersion 或其他等价值。它与 `contentHash` 的职责不同：前者判定底层存储内容是否被并发改写，后者判定规范化业务内容是否变化。运行时 `PolicySnapshotProvider` 监听或轮询完整快照，校验通过后原子替换本机内存引用；加载失败继续使用 last-known-good，首次启动没有有效快照则 fail-fast。

当前没有需要消费“策略已发布”事实的独立业务用例，因此不引入事件投影链路。技术实现的 watch/change notification 不是领域事件；未来出现真实跨边界消费者时，再定义独立集成事件和可靠发布机制。

### Nacos adapter

Nacos 能较好匹配当前网关的 Definition 编辑、完整配置发布、变更通知、历史和回退需求，因此作为优先 infrastructure adapter，但不是目标领域形态。该 adapter 使用以下配置资源：

```text
namespace = <environment>
group     = GATEWAY
Definition dataId = gateway-endpoints.yaml
                    gateway-client-app-policies.yaml
                    gateway-channel-access-policies.yaml
                    gateway-channel-credential-policies.yaml
                    gateway-route-access-policies.yaml
Snapshot dataId   = gateway-ingress-policy.yaml
```

Nacos adapter 使用五份 Definition dataId 和一份 Snapshot dataId。每份 Definition 的 sourceRevision 是原始内容 MD5；Admin 保存和回退恢复都使用 MD5 CAS。Snapshot CAS 继续保证 `snapshotVersion` 单调递增。运行时 adapter 只监听 Snapshot，重新读取完整 content，完成结构、contentHash、EndpointCatalog、deliveryRouteId 和领域约束校验，再原子切换本机引用。

当前实现使用 Spring Cloud Alibaba 的 Nacos Config 客户端，但不使用 `spring.config.import` 绑定 Snapshot。专用 adapter 直接读写 Definition 和 Snapshot；监听回调直接消费 Nacos 推送的完整 Snapshot content，避免收到通知后再次读取客户端旧缓存，周期恢复任务仍会主动重新读取。读取或校验失败时继续使用内存中的最后有效版本，并按有界间隔恢复连接；低于本机 current 的过期观测只记录告警并拒绝应用，同版本不同 `contentHash` 仍视为非法内容。每次接受有效内容后通过 `LastKnownGoodPolicySnapshotStore` 保存；默认文件实现使用临时文件、fsync 和原子替换，后续可替换为共享目录、Redis 或对象存储。

Nacos MD5 与 `contentHash` 不要求相等，也不能互相替代。若 Definition 字段顺序、空白或格式变化导致 MD5 改变，但规范化后的 `contentHash` 未改变，显式 Publish 以 `NO_CHANGES` 失败，不生成新的 snapshotVersion。Definition 可以在 Nacos UI 编辑；Snapshot 只能由控制面通过统一序列化器写入，任何直接修改 Snapshot dataId 的行为都不受支持，并由权限和审计阻断。

Snapshot dataId 的写权限只授予 gateway 控制面发布身份，运行时消费身份只读；Definition dataId 可以通过受控 Admin 或 Nacos UI 编辑。历史直接依赖 Nacos History，其保留周期必须覆盖业务回退窗口。回退由控制面读取历史 Snapshot、恢复 Definition，并重新生成更大的 snapshotVersion；禁止在控制台直接回滚 Snapshot dataId。

Nacos 官方配置模型以 `namespaceId + groupName + dataId` 标识一份完整 content，并提供发布、监听、历史和回滚能力：[配置管理概览](https://nacos.io/docs/latest/manual/user/config/overview/)、[发布、查询与监听](https://nacos.io/docs/latest/manual/user/config/publish-query-listen/)。CAS 发布使用上一内容 MD5，避免并发发布互相覆盖：[Java SDK CAS 发布](https://www.nacos.io/en/docs/next/manual/user/java-sdk/usage/)。替换 Nacos 时只更换 Provider/Publisher adapter，不修改领域策略、Admin 用例或快照结构。

## 七、应用端口与分层

| 端口 | 职责 | 目标实现 |
| --- | --- | --- |
| 五类 `*DefinitionRepository` | 分别读取 EndpointCatalog 与四类策略 Definition | Nacos 五个独立 dataId；本地 ConfigurationProperties 五个只读实现 |
| 五类 `Writable*DefinitionRepository` | 在对应读取端口基础上提供 CAS 保存和回退恢复能力 | Nacos、数据库或其他可写持久化 adapter |
| `PolicySnapshotProvider` | 提供当前完整、不可变快照 | 配置中心/数据库/GitOps读取 adapter + 内存 AtomicReference |
| `PolicySnapshotPublisher` | 以 sourceRevision 做 CAS 发布 | Nacos、数据库或其他配置发布 adapter |
| `PolicySnapshotHistory` | 按业务版本读取历史快照，供控制面回退 | Nacos v3 Admin History API adapter |
| `PolicyRollbackState` | 持久标识回退未完成并阻断普通发布 | Redis 无 TTL 状态键 |
| `ChannelCredentialAuthenticator` | 使用 Channel Credential 验证 Request Signature | 请求签名 adapter |
| `BrowserSessionVerifier` | 在线验证 Browser Session，并区分凭据无效、验证依赖不可达与响应协议非法 | user-auth adapter 或共享 Session Store adapter |
| `ReplayGuard` | 原子登记 timestamp/nonce 证据 | Redis adapter |
| `TrafficGuard` | 执行入口流量策略 | Redis RateLimiter/Sentinel adapter |
| `SecurityAuditPublisher` | 输出安全和 Admin 变更审计 | 日志/审计存储 adapter |

领域层拥有策略、凭据、Subject、Subject 认证结果、Decision 和纯规则；application 编排请求判定与 Admin 发布并声明 Browser Session 验证端口；infrastructure 实现快照存储/分发、安全协议、Redis 和审计；interfaces 处理 WebFlux、Header、`SubjectAuthenticator<T>` 和 Admin REST；boot 只装配运行时。

领域和应用能力保持稳定边界；Nacos、策略协调 Redis、安全及防重放 Redis 等可替换运行时技术分别位于独立 infrastructure adapter 模块，boot 只选择并装配具体实现。本地 `ConfigurationProperties` Definition 不引入额外中间件，作为共享 infrastructure 的默认只读实现；它不提供可写 Definition Repository 或 `PolicySnapshotPublisher`。只有可写 Definition、Publisher、History、协调锁和 rollbackState 全部存在时，才装配 Admin 命令、Facade 和 REST 入口。

## 八、信任边界

### 1. 外部到 gateway

- TLS、Channel Credential Authentication、timestamp/nonce 防重放和限流在 gateway 或其前置受控入口完成。
- 所有受保护 Header 先删除再根据 TrustedRequestContext 重写。允许转发的非可信声明也必须先删除外部同名 Header，再根据独立的清洗结果重新写入，不能与可信上下文混装。
- 原始 Authorization 和 Cookie 按独立 forwarding policy 处理。

至少写入：

| Header | 来源 | 信任属性 |
| --- | --- | --- |
| `X-Client-App-Id` | 已匹配 ENABLED IngressClientAppPolicy 的请求声明 | 可信的已登记应用上下文，不等价于调用方独立身份认证 |
| `X-Client-Platform` | ClientAppId 唯一绑定的 platform | 可信事实 |
| `X-Client-Version` | `DeclaredClientMetadata.clientVersion` | 仅经过格式和长度清洗的调用方声明，不是可信事实，不得用于认证或授权 |
| `X-Channel-Code` | 已验证 ChannelContext | 可信事实 |
| `X-Subject-Type` | HOST_SESSION 或 BROWSER_SESSION | 可信事实 |
| `X-User-Id` | AuthenticatedSubject.subjectId | 可信事实 |
| `X-Session-Id` | AuthenticatedSubject.loginSessionId | 可信事实 |

### 2. 内部可信网络

“可信内网”不是代码约定，而是部署前提，必须由平台/基础设施团队通过以下机制保证：

- 下游服务不暴露公网 Listener 或公网 LoadBalancer。
- 安全组、防火墙或 Kubernetes NetworkPolicy 只允许 gateway 和明确授权的内部 workload 访问服务端口。
- 外部入口不能路由到内部管理地址，受保护 Header 在公网边界统一清除。
- 部署验收和持续扫描验证网络规则没有漂移。

当前暂不增加 service identity、delegation 或 on-behalf-of。内部服务与 Job 也分配 ClientApp，`platform` 表示调用载体而非操作系统（`SERVICE`、`JOB`），受 IngressClientAppPolicy 同一套状态与唯一性规则约束；代理终端用户请求时保留并传播最终用户 Client、Channel、User、Session、Subject Type 与 trace 上下文。只要上述网络隔离无法被证明，这个简化就不成立，届时必须另行设计 workload identity 等零信任方案。

## 九、当前实现状态与后续边界

当前代码已经实现：

1. API、domain、application、共享 infrastructure、具体策略/安全/Redis adapter、interfaces 和 boot 的 Maven 模块边界。
2. EndpointCatalog、四类入口策略、完整 IngressPolicySnapshot、规范化 contentHash、业务版本与 sourceRevision CAS。
3. 四类策略的 Admin Definition 保存和显式完整 Snapshot 发布；无变化发布失败且不增加版本。
4. Nacos 五份 Definition 与完整 Snapshot adapter：各 dataId MD5 CAS、Snapshot 完整内容监听、结构/业务 hash/领域校验、内存原子切换、last-known-good 和断线恢复。
5. JWT/Reference Token 的 Spring Security 技术适配，并归一化为不感知 Token 格式的 AuthenticatedSubject。
6. active/grace Channel Credential、三种签名契约、常量时间比较、时间窗口和 Redis `SET NX + TTL` 跨实例防重放。
7. Endpoint 解析、入口判定、受保护 Header 重写、clientVersion 非可信声明转发，以及 Authorization/Cookie 独立转发策略。
8. Nacos History 回退、Redis publishLock/rollbackLock 和永久 rollbackState；回退可重入并允许更换目标版本。
9. 本地配置由共享 infrastructure 作为默认只读 Definition/Snapshot 实现；独立 policy-local adapter 已废弃。
10. Browser Session 的 `BrowserSessionVerifierAdapter` 在线验证 adapter（固定内部路径，按受管理的 `serviceId` 经 Spring Cloud LoadBalancer/Nacos 服务发现调用 user-auth；无可用实例时 fail-closed）与 `X-Subject-Type` 覆盖写（framework `RequestHeader.SUBJECT_TYPE` 统一常量）；Redis idle TTL 的滑动续期由 user-auth 管理，gateway 不写 Cookie。

尚未完成的目标能力：

1. EndpointManifest 聚合、deliveryRouteId 到实际 SCG routeId 的启动校验，以及下游接口漂移的自动契约校验。
2. 高危凭据撤销的实例确认或流量摘除机制；普通策略更新仍接受配置传播窗口。

已交付的浏览器会话能力：`USER_AUTH.BROWSER_SESSION_API` Endpoint（`BROWSER_SESSION_REQUIRED` + Cookie 白名单转发）已登记，Browser Session 端点由 user-auth 按 `/api/user-auth/browser-sessions/**` 前缀逐个交付。宿主 App 内 WebView H5 是该通用 Browser Session 的一种获取和使用场景，不构成独立入口策略类型。

## 十、测试边界

- 领域测试：覆盖所有枚举组合、不变量、audience/scope 的 AND/子集语义、Browser Session credentialExpiresAt、active/grace Channel Credential 轮换与绑定，以及 forwarding policy。
- 契约测试：覆盖快照内 EndpointCatalog 的唯一匹配、策略全覆盖、deliveryRouteId 到 SCG routeId 的映射，以及下游 method/path 漂移。
- 应用测试：使用 fake Definition、Publisher、History、RollbackState 和 LockExecutor 覆盖无变化发布、混合 Definition 阻断、锁顺序、回退失败保留状态及幂等恢复。
- infrastructure 测试：覆盖签名 active/grace/replay、规范化 contentHash，以及 Nacos codec、CAS/watch/load、非法/倒退快照拒绝、仅表示变化时不生成业务版本、首次启动 fail-fast、last-known-good 与历史查找；Browser Session adapter 仍需覆盖在线状态与生命周期。
- interfaces 测试：覆盖伪造 Header 清除、Authorization/Cookie 独立转发和 Deny 不调用下游。
- 集成测试：独立 `gateway-integration-tests` 模块使用 Testcontainers Nacos 3.0.3 验证真实 MD5 CAS 和两个 Store 的监听传播；Docker 不可用时明确跳过。Redis 原子防重放及传播窗口内“单请求只使用一个快照版本”的端到端场景仍待补充。
