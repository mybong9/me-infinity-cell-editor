# ME无限原件编辑

独立 Forge 1.20.1 模组。选择多种物品、流体或 Mek 化学资源，生成可装入 AE2 ME 驱动器的无限供给元件，并编辑、导出独立 KubeJS 脚本。物品和流体按 NBT 精确区分。

## 1.0.5 跨整合包迁移和物品表达式

导出后，将以下文件按相同相对路径复制到另一整合包的 `kubejs` 文件夹。无需复制原整合包的绝对路径、草稿或编辑器 JAR：

- `startup_scripts/me_infinity_selectors.js`
- `startup_scripts/me_infinity_runtime.js`
- `client_scripts/me_infinity_catalog.js`（JEI 可选）
- `server_scripts/me_infinity_editor/` 中需要的元件脚本
- `config/me_infinity_editor/catalog.json`

目标整合包必须是 Forge 1.20.1，并安装 AE2 15.4.10+、KubeJS 6 及其前置；物品、流体、化学资源和配方所需的模组也必须存在。化学品仍需要 Mekanism 和 Applied Mekanistics。首次安装或更新启动脚本后，完全退出并重启游戏；多人游戏服务端也需安装，使用 JEI 时客户端同步这些文件。合并已有 `catalog.json` 时按元件 ID 合并，不要覆盖目标包的其他元件。

在 `config/me_infinity_editor/catalog.json` 对应元件中编辑 `itemSelector`，例如：

```json
{
  "my_infinity_cell": {
    "nbt": "{Items:[],Keys:[],display:{Name:'{\"text\":\"通配符无限元件\"}'}}",
    "itemSelector": "(minecraft:*_planks | #forge:ingots) & !minecraft:gold_ingot"
  }
}
```

`nbt` 保留编辑器导出的原值即可；其中精确选择的物品及 NBT、流体和化学资源仍会供给。`itemSelector` 额外选择不带 NBT 的物品，匹配目标整合包的当前物品注册表与标签，不会把原整合包的匹配结果写死。空字符串表示不额外选择物品；旧版直接保存 SNBT 字符串的目录条目仍可使用。重新导出同一 ID 时保留已填写的表达式。

| 写法 | 含义 |
| --- | --- |
| `minecraft:diamond` | 精确物品 ID，省略命名空间时使用 `minecraft` |
| `thermal:*`、`*:copper_ingot` | `*` 匹配任意长度，`?` 匹配单个字符 |
| `#forge:ingots` | 精确物品标签 ID，标签名本身不支持通配符 |
| `a | b`、`a || b` | 或 |
| `a & b`、`a && b` | 且 |
| `!a` | 非 |
| `(a | b) & !c` | 括号分组，默认优先级为非、且、或 |

例如 `thermal:* & !thermal:*_spawn_egg`；也可只排除：`* & !minecraft:*_spawn_egg`。表达式不会修改已有精确资源列表，排除条件只作用于表达式匹配的物品。精确资源列表仍为最多 256 种；表达式展开不截断为 256 种，`*` 可能使终端列出整个整合包的物品。不存在的 ID 或标签不会产生匹配，语法错误会在 KubeJS 中报告。

修改目录后执行 `/reload` 更新配方和 JEI，重新获取元件。表达式写入新元件 NBT，旧元件不会自动换成新的表达式。表达式仅选择物品；流体和化学品继续使用编辑器的精确资源选择。

## 1.0.4 多资源支持

- 同一元件可混合保存物品、流体、气体、灌注、颜料、浆液，总上限为 256 种。
- 流体直接使用 AE2 流体通道，不是无限供给装桶物品。
- 安装 Mekanism 后显示四种化学类型；将这些类型接入 ME 网络还需安装 Applied Mekanistics（appmek）。缺少桥接时会显示提示，不会把化学物当成普通物品保存。
- 点击左侧类型按钮切换资源库，按名称或资源 ID 搜索；支持手动 ID、JEI 拖放和标记。Mek 1.20.1 化学类型按注册身份区分，本身不使用物品式 NBT。
- 物品和流体的 NBT 保留到草稿、元件及导出的独立脚本中。
- 每种资源向 ME 网络报告 int64 最大数量。元件只供给，不接收存入。
- 更新本版后重新导出一次并重启，以安装新的多资源运行脚本。移除编辑器时，化学资源仍需保留 Mekanism 和 Applied Mekanistics。
- 合成配方网格使用物品材料；流体及化学材料的机器配方通过自定义机器 JSON 配置。

## 1.0.3 独立导出

- 元件贴图不叠加内容物图标；安装编辑器时悬浮提示仍提供图标网格。
- 独立导出会同时写入 `kubejs/startup_scripts/me_infinity_runtime.js`、`kubejs/client_scripts/me_infinity_catalog.js`、`kubejs/server_scripts/me_infinity_editor/<ID>.js` 和 `kubejs/config/me_infinity_editor/catalog.json`。
- 必须保留这些文件。首次导出后重启游戏，注册 `kubejs:me_infinity_cell`；以后更新内容或配方可执行 `/reload`。
- 通过独立脚本生成、合成的 KubeJS 元件不再要求安装编辑器 JAR；AE2、KubeJS 及其前置仍须保留。
- 旧的 `me_infinity_editor:infinity_cell` 不会因卸载编辑器自动转换。先用新版重新导出、重启、重新获取 KubeJS 元件，再卸载编辑器。

## 1.0.1 更新

- 每种物品向 ME 网络报告 int64 最大数量：`9,223,372,036,854,775,807`，持续提取不会耗尽。
- 新增“存储目录”页：显示本机实例、草稿和 KubeJS 导出目录的绝对地址，支持复制地址、打开文件夹。
- 窗口、分区、按钮、输入框和物品槽使用平滑圆角。
- 右上角“按键”可直达原版按键绑定设置；在“ME无限原件编辑”分类修改打开编辑器和标记物品的按键，默认 F8 / M。帮助页会显示当前绑定。
- 旧版 JAR 应先移出 `mods`，只保留一个版本。现有元件无需重新生成即可采用新的数量显示逻辑。

## 安装

- Java 17，Minecraft 1.20.1，Forge 47.4.0 或更高的 47.x。
- 必装 AE2 **15.4.10 或更高的 Forge 1.20.1 版本**、KubeJS 6（2001.6.0+）。
- 同时安装这些依赖各自要求的前置，例如 AE2 的 GuideME、KubeJS 的 Rhino 与 Architectury。
- JEI 15 可选。不装 JEI 仍可使用内置物品库、搜索、背包选择、主手选择、手动 ID 和 NBT 输入。
- 使用编辑器时本模组安装到客户端和服务器两端。独立导出的 KubeJS 元件按上文步骤安装运行脚本后，可移除编辑器 JAR。

## 操作

1. 进入游戏后按 **F8** 打开编辑器；按键可在控制设置中修改。
2. 内容页填写元件 ID（小写字母、数字、下划线）和显示名称。
3. 左侧物品库点击添加；可按显示名称或注册 ID 搜索。切换“背包”后可选取带 NBT 的真实物品。点击“添加主手”也可读取主手物品。
4. 手动输入如 `minecraft:diamond`，NBT 栏输入 `{variant:"a",display:{Name:'{"text":"样品"}'}}`。NBT 栏可留空。
5. 在背包槽、JEI 列表或 JEI 收藏的物品上按 **M**，可以连续标记多个物品，再按 F8 编辑。JEI 可以把物品直接拖入内容列表或配方槽。
6. 同一物品的不同 NBT 会作为不同类型保留；完全相同的物品和 NBT 自动去重。一个元件最多 256 种。点击内容槽移除，左右箭头翻页。
7. “生成元件”将请求服务端发放元件；需要创造模式或管理员权限。放入 ME 驱动器后可无限提取所列物品。此元件只供给，不接收或销毁网络存入的物品。

## 配方设置

每个元件可建立多个配方。左侧选材料，再点击 3×3 虚拟槽；右键清空槽位。JEI 也可直接拖到槽位。虚拟槽不消耗实物。

- 有序合成：保存布局并自动裁掉外围空行、空列。
- 无序合成：每个非空槽代表一份材料。
- 熔炉、高炉、烟熏炉、营火、切石机：只允许填一个材料槽；烹饪配方可设置 tick 时间、经验。
- 材料带 NBT 时导出精确 NBT 匹配。
- 自定义机器 JSON：复制其他模组支持的配方 JSON，点击“粘贴 JSON”。将原本的完整输出对象替换为字符串 `"$OUTPUT"`，导出器会替换为带 NBT 的元件输出对象。例如：

```json
{
  "type": "minecraft:crafting_shapeless",
  "ingredients": [{"item": "minecraft:diamond"}],
  "result": "$OUTPUT"
}
```

自定义机器配方仍须遵循目标模组的序列化格式。`$OUTPUT` 替换后是含 `item`、`count`、`nbt` 的对象；如果机器使用不同的输出结构，应在导出的脚本中适配。这里提供 JSON 入口，并未移植蜜蜂编辑器对每个第三方机器的专用表单。

## 草稿与导出

- “保存草稿”：`config/me_infinity_editor/drafts/<ID>.json`。
- “读取 ID”：按照当前内容页填写的 ID 读取。退出编辑器时也会自动保存。
- “导出 KubeJS”：`kubejs/server_scripts/me_infinity_editor/<ID>.js`。覆盖前保留上一版 `.bak`。
- 首次独立导出需重启。以后执行 `/reload` 重新加载配方与 JEI 展示数据；没有设置配方时不会添加免费合成配方。
- 多人游戏导出文件在操作端本地；需将脚本放入服务器对应目录并在服务器执行 `/reload`。
- 草稿和已生成元件相互独立；修改草稿后应重新生成或重新合成元件。

界面物品库、虚拟槽、分区表单和明暗配色组织，独立实现。
