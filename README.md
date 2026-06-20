# RandomCraft (Minecraft 1.21.1 Forge)

Mod tự động xáo trộn công thức chế tạo (Crafting Table) mỗi N phút, hoạt động với cả recipe của mod khác.

## Tính năng
- Shuffle output của tất cả `minecraft:crafting` recipe (shaped + shapeless, cả modded).
- Chỉ ảnh hưởng Crafting Table — không đụng furnace, brewing, v.v.
- Chu kỳ tùy chỉnh qua config.
- Client vanilla vẫn chơi được (chỉ cần cài mod ở server); recipe book tự sync qua `ClientboundUpdateRecipesPacket`.

## Config
File: `config/randomcraft-common.toml`

| Key | Mặc định | Mô tả |
|-----|----------|------|
| `shuffleIntervalSeconds` | `600` | Chu kỳ shuffle (giây). 600 = 10 phút. |
| `shuffleOnServerStart` | `true` | Shuffle ngay khi server khởi động. |
| `broadcastMessage` | `true` | Chat thông báo khi shuffle. |
| `includeModdedRecipes` | `true` | Có shuffle recipe của mod khác không. |
| `recipeBlacklist` | `[]` | Danh sách recipe ID không shuffle. |
| `itemBlacklist` | `["minecraft:crafting_table","minecraft:chest"]` | Item output cấm shuffle. |

## Build
```
gradlew build
```
File jar ở `build/libs/`.

## Yêu cầu
- Minecraft 1.21.1
- Forge 52.0.40+
- Java 21
