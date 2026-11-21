# AH Sell

Meteor Client addon — tự động vòng lặp: **bán item** → **chờ N phút** → **mở `/ah`** → **vào "item của bạn"** → **huỷ listing** → lặp lại.

Author: **yogurt** · Target: **Minecraft 1.21.11 / Meteor 1.21.11-SNAPSHOT** · Java 21 · Gradle 9.8.1

## Build

```
gradlew build
```

Jar ra ở `build/libs/ah-sell-1.1.0.jar` (bỏ file có đuôi `-sources`). Copy vào `.minecraft/mods/` (cần Meteor Client 1.21.11 + Fabric API).

Không cài Java? Đẩy cả thư mục này lên một repo GitHub, vào tab **Actions** → chạy "Build" → tải file trong mục **Artifacts** (`ah-sell-jar`). Workflow có sẵn ở `.github/workflows/build.yml`.

## Cài đặt trong game

Module ở category **AH Sell** → `auto-ah-plus`.

### Nhóm General
| Setting | Ý nghĩa |
|---|---|
| `sell-price` | Giá bán, ví dụ `100m`, `500k`, `1b` |
| `sell-command` | Lệnh mở GUI bán, mặc định `ah sellmulti` |
| `ah-command` | Lệnh mở auction house, mặc định `ah` |

### Nhóm Ban hang — chọn đồ để bán
| Setting | Ý nghĩa |
|---|---|
| `sell-slots` | Ô trong GUI bán nhiều để đặt đồ vào. Một ô: `13`. Nhiều ô: `10,11,12,13` |
| `item-filter` | Chỉ bán item có **id hoặc tên** chứa từ khoá (cách nhau dấu phẩy). Trống = bán mọi thứ. VD: `diamond,emerald,netherite` |
| `max-stacks` | Tối đa bao nhiêu stack đặt vào GUI **mỗi lượt** |
| `sell-batches` | **(mới)** Số lượt mở `sell-command` liên tiếp mỗi vòng. Tổng listing ≈ `sell-batches × max-stacks`. Hết item thì tự dừng đăng và sang pha huỷ |
| `confirm-slot` | Ô nút xanh. `-1` = **tự tìm** nút màu xanh (lime/green pane, emerald, slime…) |
| `swap-hotbar-slot` | Ô hotbar trung gian khi bốc item từ túi (0–8) |

### Nhóm Huy listing
| Setting | Ý nghĩa |
|---|---|
| `cancel-listing` | Tắt = chỉ đăng bán rồi lặp lại, không huỷ |
| `cancel-wait-minutes` | **Số PHÚT đợi** trước khi mở `/ah` để huỷ. `0` = huỷ ngay |
| `cancel-wait-seconds` | **(mới)** Cộng thêm số GIÂY (0–59) vào thời gian chờ. Ví dụ phút = `0`, giây = `30` → huỷ sau 30 giây |
| `cancel-all` | **(mới)** Bật = huỷ **tất cả** listing của các loại item đã đăng (lặp lại tới khi hết). Tắt = chỉ huỷ 1 listing như bản cũ |
| `max-cancels` | **(mới)** Giới hạn số listing huỷ mỗi vòng, tránh lặp vô hạn nếu GUI server lạ |
| `chat-announce` | Báo tiến trình lên chat |
| `my-item-name` | Tên nút ender chest `item cua ban` (không cần dấu) |
| `listing-slot` | Ô chứa món vừa bán. `-1` = tự tìm theo đúng item đã bán |
| `cancel-slot` | Ô nút huỷ. `-1` = tự tìm (barrier / kính đỏ / tên có chữ "huỷ") |

### Nhóm Khac
| Setting | Ý nghĩa |
|---|---|
| `step-delay-ticks` | Trễ giữa các bước (20 tick = 1s) |
| `loop-delay-ticks` | Trễ giữa các vòng |
| `max-retries` | Số lần thử lại một bước bị kẹt trước khi huỷ vòng |
| `close-gui-on-disable` | Đóng GUI khi tắt module |

## Luồng chạy

```
── PHA BÁN (lặp sell-batches lượt) ───────────────────────────
IDLE           → /ah sellmulti <giá>
WAIT_SELL_GUI  → chờ GUI (chưa mở thì gửi lại, tối đa max-retries)
PLACE_ITEMS    → lấy item từ túi (theo item-filter) đặt vào các ô sell-slots
                 (hết item → thoát pha bán, sang pha huỷ)
CONFIRM_SELL   → tự tìm và bấm nút màu xanh
WAIT_SOLD      → chờ GUI đóng = đã đăng xong 1 lượt
               → còn lượt (sell-batches) thì quay lại IDLE ngay
── PHA HUỶ (lặp tới khi hết listing) ─────────────────────────
CANCEL_WAIT    → đếm ngược cancel-wait-minutes + cancel-wait-seconds
               → /ah
WAIT_AH_GUI    → chờ GUI đấu giá
CLICK_MY_ITEM  → bấm ender chest "item của bạn"
CLICK_LISTING  → bấm 1 listing thuộc các loại item đã đăng
CLICK_CANCEL   → bấm nút huỷ (bấm tối đa 2 lần cho hộp xác nhận phụ)
CANCEL_REOPEN  → (cancel-all) đóng GUI, mở lại /ah → quay lại WAIT_AH_GUI
               → hết listing khớp (hoặc đủ max-cancels) thì kết thúc pha huỷ
CLOSE_GUI      → đóng GUI, cycles++
LOOP_WAIT      → đợi loop-delay-ticks → IDLE
```

Mỗi lần huỷ xong, module **mở lại `/ah`** thay vì giả định GUI còn nguyên, nên không phụ thuộc việc server đóng GUI hay giữ nguyên sau khi huỷ, và danh sách tự dồn lên khi một listing biến mất.

Mọi state đều có giới hạn: bước nào không tiến triển sau `max-retries` thì huỷ vòng và làm lại từ đầu, không bao giờ treo.

## Ghi chú

- `sell-slots`, `confirm-slot`, `listing-slot`, `cancel-slot` là **ô trong GUI** (0–53), không phải slot túi đồ.
- Item được chuyển bằng `SWAP`: item trong túi → hotbar trung gian → ô GUI. Không cần cầm item trên tay.
- `confirm-slot` / `cancel-slot` để `-1` thì module tự nhận diện theo **loại item** (nút xanh / barrier / kính đỏ), server đổi layout vẫn chạy.
- Tên item/GUI được so khớp sau khi bỏ mã màu và bỏ dấu tiếng Việt → `bán nhiều` = `ban nhieu`, `huỷ` = `huy`.
- Nếu món đồ nằm ở trang 2+ trong `/ah` thì đặt `listing-slot` thủ công.
