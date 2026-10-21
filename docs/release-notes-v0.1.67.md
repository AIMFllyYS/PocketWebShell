# PocketWebShell 0.1.67

站点数据清理的结果修正。上一份 GitHub Release 是 0.1.66，本说明覆盖 0.1.67。

## 修复：重置离线资源误报失败

网站详情里的「重置离线资源」会清除该站点同源的缓存与 Service Worker。此前在缓存**已经成功清除**的情况下，界面仍会提示「清理失败」。

原因是探测实现把两件事放进了同一个 `Promise.all`：

- 删除 Cache Storage 中的全部缓存；
- 调用 `serviceWorker.getRegistrations()` 注销 Service Worker。

离线重置为了让站点脚本无法趁机重建缓存，会先加载一个同源的惰性空文档。这个文档在 Chromium 里不被视为可用的导航目标，`getRegistrations()` 会直接抛出 `InvalidStateError: The document is in an invalid state.`。该异常让整条 Promise 链进入 reject 分支，探测结果因此变成 `failed`，即使缓存删除早已完成。

设备实测可以清楚看到分裂的结果：

```text
is_cacheDeleteVal="ok"
is_swUnregVal="ERR:InvalidStateError:Failed to get ServiceWorkerRegistration objects: The document is in an invalid state."
prodResetFinal="failed"
keysAfterVal="n=0"
```

缓存键数量已归零，但整体判定仍是失败，用户于是看到错误提示。

## 现在的行为

- **缓存删除决定结果**。删除后重新枚举 `caches.keys()`，为空即判定成功，与用户实际得到的效果一致。
- **Service Worker 注销改为尽力而为**。它在独立分支执行，成功或失败都不再翻转清理结论；在无法调用的文档里静默跳过。
- 调用方 `StorageViewModel.resetOffline` 的失败提示只在缓存确实没有清干净时出现。

因为失效文档结构上无法查询 Service Worker 注册，这里无法既保留惰性文档隔离、又要求 Service Worker 注销成功。以缓存结果为准是唯一不会误报的选择。

## 测试

- `testDebugUnitTest`：410 项通过，0 失败。
- `:app:connectedDebugAndroidTest`（API 35 模拟器）：22 项通过，0 失败。此前失败的 `offlineResetDeletesCacheStorageAndPreservesLocalStorage` 现已通过。
- 同批修正 `IosPresentationInstrumentedTest.addAndBrowserControlsRemainReachable`：浏览菜单「关闭全部标签」位于 1080×2400 首屏之外的滚动区域内，测试缺少滚动步骤。该测试此前失败，产品行为正确。

## 已知边界

- Service Worker 注销在惰性文档中无法执行，其残留由缓存与站点数据清理覆盖。
- 卸载应用或清除应用数据仍是彻底重置站点状态的最终手段。
