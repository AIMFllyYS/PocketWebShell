package com.webshell.core.webengine

/** 壳引擎的全局常量与默认值。 */
object WebEngineDefaults {

    /** 伪装 Chrome 的版本号：UA 字符串与 UA-CH metadata 必须保持一致。 */
    const val UA_MAJOR_VERSION: String = "151"
    const val UA_FULL_VERSION: String = "151.0.0.0"

    /** GREASE 品牌（对齐 Chrome UA-CH 的 Not_A Brand 条目）。 */
    const val UA_GREASE_BRAND: String = "Not_A Brand"
    const val UA_GREASE_MAJOR_VERSION: String = "99"
    const val UA_GREASE_FULL_VERSION: String = "99.0.0.0"

    /** 桌面模式 UA（Chrome 桌面版，自 Chrome 110 起 minor/build 固定为 .0.0） */
    const val DESKTOP_USER_AGENT: String =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/" + UA_FULL_VERSION + " Safari/537.36"

    /**
     * 移动模式 UA（Chrome Android 移动版，主版本与 [DESKTOP_USER_AGENT] 保持一致）。
     * 不使用 WebView 默认 UA：其含 "Version/4.0" 与 "; wv)" 标记，
     * 部分站点（Google 登录、若干移动站）会识别为内嵌壳而拒绝/降级服务。
     */
    const val MOBILE_USER_AGENT: String =
        "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/" + UA_FULL_VERSION + " Mobile Safari/537.36"

    /**
     * UA-CH metadata：UA 字符串只覆盖 `User-Agent` 头，`Sec-CH-UA-*` 请求头与
     * `navigator.userAgentData` 完全由这份 metadata 决定。
     * 每个 brand 的 brand/majorVersion/fullVersion 三字段都必填——
     * `BrandVersion.Builder.build()` 对空字段必抛 `IllegalStateException`；
     * 0.1.58–0.1.60 漏填 GREASE 品牌的 fullVersion，异常被调用方吞掉，
     * 整套 Client Hints 从未真正设置，按 UA-CH 判定的站点始终收到移动身份。
     * 纯 JVM 可构造（不触碰 Android 框架），由单元测试直接验证。
     * [mobilePlatformVersion]/[mobileModel] 仅移动模式使用，由宿主传入 Build.* 值。
     */
    fun userAgentMetadata(
        desktop: Boolean,
        mobilePlatformVersion: String = "",
        mobileModel: String = "",
    ): androidx.webkit.UserAgentMetadata =
        androidx.webkit.UserAgentMetadata.Builder()
            .setBrandVersionList(
                listOf(
                    androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                        .setBrand("Chromium")
                        .setMajorVersion(UA_MAJOR_VERSION)
                        .setFullVersion(UA_FULL_VERSION)
                        .build(),
                    androidx.webkit.UserAgentMetadata.BrandVersion.Builder()
                        .setBrand(UA_GREASE_BRAND)
                        .setMajorVersion(UA_GREASE_MAJOR_VERSION)
                        .setFullVersion(UA_GREASE_FULL_VERSION)
                        .build(),
                ),
            )
            .setPlatform(if (desktop) "Windows" else "Android")
            .setPlatformVersion(if (desktop) "10.0.0" else mobilePlatformVersion)
            .setArchitecture(if (desktop) "x86" else "")
            .setModel(if (desktop) "" else mobileModel)
            .setMobile(!desktop)
            // Chrome 桌面在 Windows x64 上恒上报 64 位；留空是移动端特征。
            .apply { if (desktop) setBitness(64) }
            .build()

    /** WebViewAssetLoader 的本地资源域名（本地 HTML 导入用真实 https 源提供） */
    const val ASSET_LOADER_HOST: String = "appassets.androidplatform.net"

    /** [android.webkit.WebViewClient.onReceivedError] 风格码：渲染进程已消失。 */
    const val ERROR_RENDERER_GONE: Int = -99

    /**
     * 桌面模式的固定布局宽。Chrome「请求桌面网站」在内核层用 980 虚拟视口并直接
     * 禁用站点 viewport meta；注入式方案做不到后者，而现代响应式站点的桌面断点
     * 普遍高于 980（Bootstrap lg=992、Tailwind lg=1024、GitHub≈1012）——980 只会
     * 得到"平板/窄桌面"布局。真机证据（HwWebview 114，Android 12）：布局宽 980 时
     * `(min-width:980px)` 媒体查询仍不命中（引擎评估宽比改写值略小），768 命中。
     * 取 1280：越过全部主流桌面断点并给引擎的评估削减留余量，配合 overview 缩放
     * 整页缩到屏宽，与 Chrome 桌面版的最终观感一致。
     */
    const val DESKTOP_VIEWPORT_WIDTH: Int = 1280

    /**
     * Chrome-style overview scale: layout width stays [DESKTOP_VIEWPORT_WIDTH],
     * then the visual viewport shrinks to the current WebView width.
     * `0` means "leave the platform default" (used when width is not known yet).
     * WebView 的 percent 以物理像素为口径（100 = 1 CSS px 对 1 物理 px），
     * 因此窄于布局宽的屏幕会小于 100、宽屏设备可能大于 100——不得钳在 100。
     */
    fun desktopInitialScalePercent(viewWidthPx: Int): Int {
        if (viewWidthPx <= 0) return 0
        return ((viewWidthPx * 100) / DESKTOP_VIEWPORT_WIDTH).coerceIn(25, 250)
    }

    /**
     * document-start 引导脚本。始终注入安全区 CSS 变量。
     * [desktopMode] 且非本地导入时，把 viewport 改成固定桌面宽度（对齐 Chrome RDS）。
     * 仅 [forceEnableZoom] 时只解锁捏合，不改 width。
     * 本地导入只用 viewport meta 观察，且永不改 width。
     */
    fun documentStartBootstrap(
        forceEnableZoom: Boolean = false,
        localApp: Boolean = false,
        desktopMode: Boolean = false,
    ): String {
        val desktopLayout = desktopMode && !localApp
        val zoomOnly = forceEnableZoom && !desktopLayout
        val rewrite = when {
            desktopLayout -> DESKTOP_VIEWPORT_REWRITE +
                if (localApp) FORCE_ENABLE_ZOOM_OBSERVE_VIEWPORT else FORCE_ENABLE_ZOOM_OBSERVE_SUBTREE
            zoomOnly -> FORCE_ENABLE_ZOOM_REWRITE +
                if (localApp) FORCE_ENABLE_ZOOM_OBSERVE_VIEWPORT else FORCE_ENABLE_ZOOM_OBSERVE_SUBTREE
            else -> ""
        }
        return DOCUMENT_START_PREFIX + rewrite + DOCUMENT_START_SUFFIX
    }

    private const val DOCUMENT_START_PREFIX =
        "(function(){" +
            "if(window.__wsBoot&&window.__wsBoot.v===2)return;" +
            "window.__wsBoot={t:Date.now(),v:2};" +
            "try{" +
            "if(!document.getElementById('ws-safe-style')){" +
            "var r=document.head||document.documentElement;" +
            "if(r){" +
            "var s=document.createElement('style');s.id='ws-safe-style';" +
            "s.textContent=':root{--ws-safe-top:0px;--ws-safe-bottom:0px;" +
            "--ws-safe-left:0px;--ws-safe-right:0px;--ws-ime-height:0px;}';" +
            "r.appendChild(s);}" +
            "}" +
            "}catch(e){}"

    private const val DOCUMENT_START_SUFFIX = "})();"

    /**
     * 桌面远程站：固定 width=980，去掉挡 overview 的 initial/minimum-scale。
     * 必须改写**所有** viewport meta——Blink 对多个 viewport meta 按后解析者覆盖先前者，
     * 抢先注入的 meta 会被站点自己的 meta 盖掉；只改第一个等于没改。
     * 无 meta 时才补建，且只在 head/documentElement 已存在时挂接
     * （document 根节点不可追加元素，否则文档损坏）；其余时机由观察器与
     * DOMContentLoaded 兜底重跑覆盖。
     */
    private const val DESKTOP_VIEWPORT_REWRITE =
        "function wsForceZoom(){try{" +
            "var W='" + DESKTOP_VIEWPORT_WIDTH + "';" +
            "var metas=document.getElementsByTagName('meta');" +
            "var found=false;" +
            "for(var i=0;i<metas.length;i++){" +
            "var m=metas[i];" +
            "if((m.getAttribute('name')||'').toLowerCase()!=='viewport')continue;" +
            "found=true;" +
            "var c=m.getAttribute('content')||'';" +
            "var parts=c.split(',').map(function(p){return p.trim();}).filter(function(p){" +
            "if(!p)return false;" +
            "var k=p.split('=')[0].trim().toLowerCase();" +
            "return k!=='width'&&k!=='initial-scale'&&k!=='minimum-scale'" +
            "&&k!=='user-scalable'&&k!=='maximum-scale';" +
            "});" +
            "parts.unshift('width='+W);" +
            "parts.push('user-scalable=yes');" +
            "parts.push('maximum-scale=10');" +
            "var n=parts.join(',');" +
            "if(n!==c)m.setAttribute('content',n);" +
            "}" +
            "if(!found){" +
            "var head=document.head||document.documentElement;" +
            "if(head){var nm=document.createElement('meta');nm.setAttribute('name','viewport');" +
            "nm.setAttribute('content','width='+W+',user-scalable=yes,maximum-scale=10');" +
            "head.appendChild(nm);}" +
            "}" +
            "}catch(e){}}" +
            "window.__wsForceZoom=wsForceZoom;" +
            "wsForceZoom();" +
            "document.addEventListener('DOMContentLoaded',wsForceZoom);"

    private const val FORCE_ENABLE_ZOOM_REWRITE =
        "function wsForceZoom(){try{" +
            "var metas=document.getElementsByTagName('meta');" +
            "for(var i=0;i<metas.length;i++){" +
            "var m=metas[i];" +
            "if((m.getAttribute('name')||'').toLowerCase()!=='viewport')continue;" +
            "var c=m.getAttribute('content')||'';" +
            "var n=c.replace(/user-scalable\\s*=\\s*[^,\\s]+/ig,'user-scalable=yes')" +
            ".replace(/maximum-scale\\s*=\\s*[^,\\s]+/ig,'maximum-scale=10');" +
            "if(!/user-scalable\\s*=/i.test(n))n=n?n.replace(/,?\\s*$/,'')+',user-scalable=yes':'user-scalable=yes';" +
            "if(!/maximum-scale\\s*=/i.test(n))n=n?n.replace(/,?\\s*$/,'')+',maximum-scale=10':'maximum-scale=10';" +
            "if(n!==c)m.setAttribute('content',n);}" +
            "}catch(e){}}" +
            "window.__wsForceZoom=wsForceZoom;" +
            "wsForceZoom();" +
            "document.addEventListener('DOMContentLoaded',wsForceZoom);"

    private const val FORCE_ENABLE_ZOOM_OBSERVE_SUBTREE =
        "try{new MutationObserver(wsForceZoom).observe(document," +
            "{childList:true,subtree:true,attributes:true,attributeFilter:['content','name']});}catch(e){}"

    private const val FORCE_ENABLE_ZOOM_OBSERVE_VIEWPORT =
        "try{var r=document.head||document.documentElement||document;" +
            "new MutationObserver(wsForceZoom).observe(r," +
            "{childList:true,subtree:false,attributes:true,attributeFilter:['content','name']});" +
            "var metas=r.getElementsByTagName('meta');" +
            "for(var i=0;i<metas.length;i++){" +
            "if((metas[i].getAttribute('name')||'').toLowerCase()==='viewport')" +
            "try{new MutationObserver(wsForceZoom).observe(metas[i],{attributes:true,attributeFilter:['content','name']});}catch(e){}" +
            "}}catch(e){}"
}
