package yogurt.ahsell.module;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import yogurt.ahsell.AhSellAddon;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * AutoAhPlus - yogurt
 * Minecraft 1.21.11 / Meteor Client
 *
 * Mot vong lap gom 2 pha:
 *
 *  PHA BAN   IDLE -> /ah sellmulti -> cho GUI -> dat item vao slot ban
 *            -> bam nut xanh -> cho GUI dong
 *
 *  PHA HUY   cho N phut (co thong bao chat) -> /ah -> bam "item cua ban"
 *            -> bam mon vua ban -> bam nut huy -> dong GUI
 *
 * Moi buoc deu co gioi han thu lai, khong bao gio treo vo han.
 */
public class AutoAhPlus extends Module {

    // -- Settings ------------------------------------------------------------

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgSell = settings.createGroup("Ban hang");
    private final SettingGroup sgCancel = settings.createGroup("Huy listing");
    private final SettingGroup sgMisc = settings.createGroup("Khac");

    // General
    private final Setting<String> sellPrice = sgGeneral.add(new StringSetting.Builder()
        .name("sell-price")
        .description("Gia ban - vi du: 100m, 500k, 1b")
        .defaultValue("100m")
        .build()
    );

    private final Setting<String> sellCommand = sgGeneral.add(new StringSetting.Builder()
        .name("sell-command")
        .description("Lenh mo GUI ban nhieu item (khong can dau /).")
        .defaultValue("ah sellmulti")
        .build()
    );

    private final Setting<String> ahCommand = sgGeneral.add(new StringSetting.Builder()
        .name("ah-command")
        .description("Lenh mo auction house de huy listing (khong can dau /).")
        .defaultValue("ah")
        .build()
    );

    // Sell phase
    private final Setting<String> sellSlots = sgSell.add(new StringSetting.Builder()
        .name("sell-slots")
        .description("Slot trong GUI ban nhieu de dat do vao. Nhieu slot thi cach nhau dau phay. Vi du: 13 hoac 10,11,12,13")
        .defaultValue("13")
        .build()
    );

    private final Setting<String> itemFilter = sgSell.add(new StringSetting.Builder()
        .name("item-filter")
        .description("Chi ban nhung item co ten chua tu khoa nay (cach nhau dau phay). De trong = ban moi thu.")
        .defaultValue("")
        .build()
    );

    private final Setting<Integer> maxStacks = sgSell.add(new IntSetting.Builder()
        .name("max-stacks")
        .description("Toi da bao nhieu stack duoc dat vao GUI moi vong.")
        .defaultValue(1)
        .min(1)
        .sliderRange(1, 8)
        .build()
    );

    private final Setting<Integer> sellBatches = sgSell.add(new IntSetting.Builder()
        .name("sell-batches")
        .description("So lan mo sell-command lien tiep moi vong. Moi lan dang toi da max-stacks stack, nen tong so listing = sell-batches x max-stacks. Het item thi dung som.")
        .defaultValue(3)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Integer> confirmSlot = sgSell.add(new IntSetting.Builder()
        .name("confirm-slot")
        .description("Slot nut xac nhan mau xanh. -1 = tu tim nut mau xanh.")
        .defaultValue(-1)
        .min(-1)
        .sliderRange(-1, 53)
        .build()
    );

    private final Setting<Integer> swapSlot = sgSell.add(new IntSetting.Builder()
        .name("swap-hotbar-slot")
        .description("Slot hotbar dung lam trung gian khi boc item tu tui (0-8).")
        .defaultValue(8)
        .min(0)
        .max(8)
        .sliderRange(0, 8)
        .build()
    );

    // Cancel phase
    private final Setting<Boolean> cancelEnabled = sgCancel.add(new BoolSetting.Builder()
        .name("cancel-listing")
        .description("Sau khi dang ban thi mo /ah de huy listing.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> cancelWaitMinutes = sgCancel.add(new IntSetting.Builder()
        .name("cancel-wait-minutes")
        .description("Doi bao nhieu PHUT truoc khi bat dau qua trinh huy listing (0 = huy ngay).")
        .defaultValue(5)
        .min(0)
        .max(240)
        .sliderRange(0, 60)
        .build()
    );

    private final Setting<Integer> cancelWaitSeconds = sgCancel.add(new IntSetting.Builder()
        .name("cancel-wait-seconds")
        .description("Cong them bay nhieu GIAY vao cancel-wait-minutes. Vi du: phut = 0, giay = 30 -> huy sau 30 giay.")
        .defaultValue(0)
        .min(0)
        .max(59)
        .sliderRange(0, 59)
        .build()
    );

    private final Setting<Boolean> cancelAll = sgCancel.add(new BoolSetting.Builder()
        .name("cancel-all")
        .description("Huy TAT CA listing cua cac item da dang (lap lai cho toi khi het). Tat = chi huy 1 listing nhu ban cu.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Integer> maxCancels = sgCancel.add(new IntSetting.Builder()
        .name("max-cancels")
        .description("Gioi han so listing huy moi vong (de khong lap vo han neu GUI server la).")
        .defaultValue(30)
        .min(1)
        .sliderRange(1, 100)
        .visible(cancelAll::get)
        .build()
    );

    private final Setting<Boolean> chatAnnounce = sgCancel.add(new BoolSetting.Builder()
        .name("chat-announce")
        .description("Thong bao tien trinh len chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<String> myItemName = sgCancel.add(new StringSetting.Builder()
        .name("my-item-name")
        .description("Ten nut 'item cua ban' trong /ah (khong can dung dau).")
        .defaultValue("item cua ban")
        .build()
    );

    private final Setting<Integer> listingSlot = sgCancel.add(new IntSetting.Builder()
        .name("listing-slot")
        .description("Slot chua mon vua ban trong danh sach. -1 = tu tim theo item da ban.")
        .defaultValue(-1)
        .min(-1)
        .sliderRange(-1, 53)
        .build()
    );

    private final Setting<Integer> cancelSlot = sgCancel.add(new IntSetting.Builder()
        .name("cancel-slot")
        .description("Slot nut huy listing. -1 = tu tim (barrier / kinh do / ten co chu 'huy').")
        .defaultValue(-1)
        .min(-1)
        .sliderRange(-1, 53)
        .build()
    );

    // Misc
    private final Setting<Integer> stepDelay = sgMisc.add(new IntSetting.Builder()
        .name("step-delay-ticks")
        .description("Do tre giua cac buoc (20 ticks = 1 giay).")
        .defaultValue(20)
        .min(5)
        .sliderRange(5, 100)
        .build()
    );

    private final Setting<Integer> loopDelay = sgMisc.add(new IntSetting.Builder()
        .name("loop-delay-ticks")
        .description("Doi bao nhieu ticks sau moi vong lap.")
        .defaultValue(40)
        .min(10)
        .sliderRange(10, 6000)
        .build()
    );

    private final Setting<Integer> maxRetries = sgMisc.add(new IntSetting.Builder()
        .name("max-retries")
        .description("So lan thu lai mot buoc bi ket truoc khi huy vong.")
        .defaultValue(4)
        .min(1)
        .sliderRange(1, 10)
        .build()
    );

    private final Setting<Boolean> closeOnDeactivate = sgMisc.add(new BoolSetting.Builder()
        .name("close-gui-on-disable")
        .description("Dong GUI dang mo khi tat module.")
        .defaultValue(true)
        .build()
    );

    // -- Items dung de nhan dien nut ------------------------------------------

    private static final List<Item> GREEN_ITEMS = List.of(
        Items.LIME_STAINED_GLASS_PANE, Items.GREEN_STAINED_GLASS_PANE,
        Items.LIME_DYE, Items.GREEN_DYE,
        Items.EMERALD, Items.EMERALD_BLOCK,
        Items.LIME_WOOL, Items.GREEN_WOOL,
        Items.LIME_CONCRETE, Items.GREEN_CONCRETE,
        Items.LIME_CONCRETE_POWDER, Items.GREEN_CONCRETE_POWDER,
        Items.LIME_TERRACOTTA, Items.GREEN_TERRACOTTA,
        Items.LIME_CARPET, Items.GREEN_CARPET,
        Items.SLIME_BALL
    );

    private static final List<Item> RED_ITEMS = List.of(
        Items.BARRIER,
        Items.RED_STAINED_GLASS_PANE,
        Items.RED_DYE, Items.RED_WOOL, Items.RED_CONCRETE,
        Items.RED_TERRACOTTA, Items.RED_CARPET,
        Items.BARRIER
    );

    // -- State machine -------------------------------------------------------

    private enum State {
        IDLE,
        WAIT_SELL_GUI,
        PLACE_ITEMS,
        CONFIRM_SELL,
        WAIT_SOLD,
        CANCEL_WAIT,
        WAIT_AH_GUI,
        CLICK_MY_ITEM,
        CLICK_LISTING,
        CLICK_CANCEL,
        CANCEL_REOPEN,
        CLOSE_GUI,
        LOOP_WAIT
    }

    private State state = State.IDLE;
    private int ticker = 0;
    private int cancelTicker = 0;
    private int retries = 0;
    private int cycles = 0;

    private final List<Integer> targets = new ArrayList<>();
    private int placedCount = 0;
    private int targetRetries = 0;
    private int pendingTarget = -1;
    private int cancelClicks = 0;
    private int lastAnnouncedMinute = -1;
    private int sellBatchesDone = 0;
    private int cancelledCount = 0;

    /**
     * Tat ca loai item da dang ban (giu nguyen qua cac lan thu lai, chi xoa khi da huy xong)
     * de pha huy biet nhung listing nao la cua minh.
     */
    private final Set<Item> soldItems = new LinkedHashSet<>();

    public AutoAhPlus() {
        super(AhSellAddon.CATEGORY, "auto-ah-plus",
            "Tu dong ban /ah sellmulti roi huy listing - yogurt",
            "ahplus", "autosell");
    }

    // -- Lifecycle -----------------------------------------------------------

    @Override
    public void onActivate() {
        resetCycle();
        soldItems.clear();
        state = State.IDLE;
        cycles = 0;
        info("Auto AH+ BẬT - giá %s, %d lượt x %d stack, chờ huỷ %s",
            sellPrice.get(), sellBatches.get(), maxStacks.get(), formatTicks(cancelWaitTicks()));
    }

    @Override
    public void onDeactivate() {
        if (closeOnDeactivate.get() && mc.player != null && mc.currentScreen != null) {
            mc.player.closeHandledScreen();
        }
        resetCycle();
        state = State.IDLE;
    }

    @Override
    public String getInfoString() {
        if (state == State.CANCEL_WAIT) return "chờ " + formatTicks(cancelTicker);
        return cycles > 0 ? "vòng " + cycles : null;
    }

    private void resetCycle() {
        ticker = 0;
        cancelTicker = 0;
        retries = 0;
        placedCount = 0;
        targetRetries = 0;
        pendingTarget = -1;
        cancelClicks = 0;
        lastAnnouncedMinute = -1;
        sellBatchesDone = 0;
        cancelledCount = 0;
    }

    // -- Tick ----------------------------------------------------------------

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        switch (state) {
            case IDLE -> tickIdle();
            case WAIT_SELL_GUI -> tickWaitSellGui();
            case PLACE_ITEMS -> tickPlaceItems();
            case CONFIRM_SELL -> tickConfirmSell();
            case WAIT_SOLD -> tickWaitSold();
            case CANCEL_WAIT -> tickCancelWait();
            case WAIT_AH_GUI -> tickWaitAhGui();
            case CLICK_MY_ITEM -> tickClickMyItem();
            case CLICK_LISTING -> tickClickListing();
            case CLICK_CANCEL -> tickClickCancel();
            case CANCEL_REOPEN -> tickCancelReopen();
            case CLOSE_GUI -> tickCloseGui();
            case LOOP_WAIT -> tickLoopWait();
        }
    }

    /** Pha 1: gui lenh mo GUI ban nhieu. */
    private void tickIdle() {
        if (!ready()) return;

        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
            ticker = stepDelay.get();
            return;
        }

        String command = buildSellCommand();
        if (command == null) return;

        parseTargets();
        placedCount = 0;
        targetRetries = 0;
        pendingTarget = -1;
        cancelClicks = 0;

        sendCommand(command);
        state = State.WAIT_SELL_GUI;
        ticker = stepDelay.get();
        retries = 0;
    }

    /** Cho GUI ban nhieu xuat hien. */
    private void tickWaitSellGui() {
        if (!ready()) return;

        if (container() != null) {
            state = State.PLACE_ITEMS;
            ticker = stepDelay.get();
            retries = 0;
            return;
        }

        if (++retries > maxRetries.get()) {
            sellFailed("Không mở được GUI bán nhiều");
            return;
        }

        if (mc.currentScreen == null) {
            String command = buildSellCommand();
            if (command != null) sendCommand(command);
        }

        ticker = stepDelay.get();
    }

    /** Dat item tu tui vao cac slot ban. */
    private void tickPlaceItems() {
        if (!ready()) return;

        ScreenHandler handler = container();
        if (handler == null) {
            sellFailed("GUI bán nhiều đã đóng");
            return;
        }

        // Buoc 2 cua 1 lan chuyen do (da nam san trong hotbar trung gian).
        if (pendingTarget >= 0) {
            click(handler, pendingTarget, swapSlot.get(), SlotActionType.SWAP);
            pendingTarget = -1;
            ticker = stepDelay.get() / 2 + 1;
            return;
        }

        if (placedCount >= targets.size() || placedCount >= maxStacks.get()) {
            state = State.CONFIRM_SELL;
            ticker = stepDelay.get();
            retries = 0;
            return;
        }

        int target = targets.get(placedCount);
        if (!inContainer(handler, target)) {
            sellFailed("Slot bán " + target + " nằm ngoài GUI (" + containerSize(handler) + " ô)");
            return;
        }

        // Slot da co do -> coi nhu dat xong.
        ItemStack existing = handler.slots.get(target).getStack();
        if (!existing.isEmpty()) {
            soldItems.add(existing.getItem());
            placedCount++;
            targetRetries = 0;
            ticker = 2;
            return;
        }

        int source = findSourceSlot(handler);
        if (source < 0) {
            if (placedCount == 0) {
                if (sellBatchesDone > 0) {
                    // Da dang it nhat 1 luot va het item de ban -> sang pha huy.
                    announce("§eHết item để bán - chuyển sang pha huỷ.");
                    closeGui();
                    startCancelPhase();
                } else {
                    sellFailed("Không tìm thấy item nào để bán (kiểm tra item-filter)");
                }
                return;
            }
            state = State.CONFIRM_SELL;
            ticker = stepDelay.get();
            retries = 0;
            return;
        }

        if (++targetRetries > maxRetries.get()) {
            announce("§cKhông đặt được item vào slot " + target + " - bỏ qua");
            placedCount++;
            targetRetries = 0;
            ticker = stepDelay.get();
            return;
        }

        ItemStack stack = handler.slots.get(source).getStack();
        soldItems.add(stack.getItem());

        int hotbarStart = playerStart(handler) + 27;

        if (source >= hotbarStart) {
            // Item dang o hotbar -> swap 1 phat vao slot ban.
            click(handler, target, source - hotbarStart, SlotActionType.SWAP);
            ticker = stepDelay.get();
        } else {
            // Item o tui -> swap vao hotbar truoc, tick sau se day sang slot ban.
            click(handler, source, swapSlot.get(), SlotActionType.SWAP);
            pendingTarget = target;
            ticker = stepDelay.get();
        }
    }

    /** Bam nut xac nhan mau xanh. */
    private void tickConfirmSell() {
        if (!ready()) return;

        ScreenHandler handler = container();
        if (handler == null) {
            onSold();
            return;
        }

        int slot = findConfirmSlot(handler);
        if (slot < 0) {
            if (++retries > maxRetries.get()) {
                sellFailed("Không tìm thấy nút xác nhận màu xanh");
                return;
            }
            ticker = stepDelay.get();
            return;
        }

        click(handler, slot, 0, SlotActionType.PICKUP);
        state = State.WAIT_SOLD;
        ticker = stepDelay.get() * 2;
    }

    /** Cho GUI ban nhieu dong lai = da dang ban xong. */
    private void tickWaitSold() {
        if (!ready()) return;

        if (container() == null) {
            onSold();
            return;
        }

        if (++retries > maxRetries.get()) {
            sellFailed("GUI bán nhiều không đóng");
            return;
        }

        // Mot so server bat bam xac nhan lan 2.
        state = State.CONFIRM_SELL;
        ticker = stepDelay.get();
    }

    /** Cho N phut roi moi mo /ah. */
    private void tickCancelWait() {
        if (cancelTicker > 0) {
            cancelTicker--;

            // Chi bao moi phut khi con tren 1 phut (cho wait ngan theo giay thi khong bao).
            int minutes = minutesLeft();
            if (chatAnnounce.get() && cancelTicker >= 1200 && minutes != lastAnnouncedMinute) {
                lastAnnouncedMinute = minutes;
                if (minutes <= 3 || minutes % 5 == 0) {
                    info("Còn §b%d phút§r sẽ mở /ah và huỷ listing...", minutes);
                }
            }
            return;
        }

        if (mc.currentScreen != null) {
            mc.player.closeHandledScreen();
            ticker = stepDelay.get();
            return;
        }

        announce("Đang mở /ah để huỷ listing...");
        sendCommand(commandOf(ahCommand.get()));
        state = State.WAIT_AH_GUI;
        ticker = stepDelay.get() * 2;
        retries = 0;
    }

    /** Cho GUI /ah. */
    private void tickWaitAhGui() {
        if (!ready()) return;

        if (container() != null) {
            state = State.CLICK_MY_ITEM;
            ticker = stepDelay.get();
            retries = 0;
            return;
        }

        if (++retries > maxRetries.get()) {
            announce("§cKhông mở được /ah");
            retryCycle();
            return;
        }

        if (mc.currentScreen == null) sendCommand(commandOf(ahCommand.get()));
        ticker = stepDelay.get();
    }

    /** Bam nut ender chest "item cua ban". */
    private void tickClickMyItem() {
        if (!ready()) return;

        ScreenHandler handler = container();
        if (handler == null) {
            retryCycle("GUI /ah đã đóng");
            return;
        }

        int slot = findMyItemSlot(handler);
        if (slot < 0) {
            if (++retries > maxRetries.get()) {
                announce("§cKhông thấy mục 'item của bạn' trong /ah");
                finishCancel();
                return;
            }
            ticker = stepDelay.get();
            return;
        }

        click(handler, slot, 0, SlotActionType.PICKUP);
        state = State.CLICK_LISTING;
        ticker = stepDelay.get() * 2;
        retries = 0;
        cancelClicks = 0;
    }

    /** Bam vao mon do vua ban trong danh sach. */
    private void tickClickListing() {
        if (!ready()) return;

        ScreenHandler handler = container();
        if (handler == null) {
            announce("§eGUI danh sách đã đóng");
            finishCancel();
            return;
        }

        int slot = findListingSlot(handler);
        if (slot < 0) {
            // Da huy it nhat 1 cai ma khong con listing nao nua = huy het roi, khong can doi lau.
            int limit = cancelledCount > 0 ? Math.min(2, maxRetries.get()) : maxRetries.get();
            if (++retries > limit) {
                if (cancelledCount == 0) announce("§cKhông thấy món vừa bán trong danh sách");
                finishCancel();
                return;
            }
            ticker = stepDelay.get();
            return;
        }

        click(handler, slot, 0, SlotActionType.PICKUP);
        state = State.CLICK_CANCEL;
        ticker = stepDelay.get() * 2;
        retries = 0;
        cancelClicks = 0;
    }

    /** Bam nut huy (co the phai bam 2 lan neu server hien xac nhan). */
    private void tickClickCancel() {
        if (!ready()) return;

        ScreenHandler handler = container();
        if (handler == null) {
            onCancelled();
            return;
        }

        int slot = findCancelSlot(handler);
        if (slot < 0) {
            if (++retries > maxRetries.get()) {
                announce("§eKhông thấy nút huỷ - đóng GUI");
                finishCancel();
                return;
            }
            ticker = stepDelay.get();
            return;
        }

        click(handler, slot, 0, SlotActionType.PICKUP);
        cancelClicks++;
        retries = 0;
        ticker = stepDelay.get();

        if (cancelClicks >= 2) onCancelled();
    }

    /** Vua huy xong 1 listing -> mo lai /ah de huy cai tiep theo (neu bat cancel-all). */
    private void onCancelled() {
        cancelledCount++;
        cancelClicks = 0;
        retries = 0;
        closeGui();

        if (!cancelAll.get() || cancelledCount >= maxCancels.get()) {
            finishCancel();
            return;
        }

        announce("§aĐã huỷ §f" + cancelledCount + "§a listing - kiểm tra listing tiếp theo...");
        state = State.CANCEL_REOPEN;
        ticker = stepDelay.get();
    }

    /** Mo lai /ah cho lan huy tiep theo. */
    private void tickCancelReopen() {
        if (!ready()) return;

        if (mc.currentScreen != null) {
            closeGui();
            ticker = stepDelay.get();
            return;
        }

        sendCommand(commandOf(ahCommand.get()));
        state = State.WAIT_AH_GUI;
        ticker = stepDelay.get() * 2;
        retries = 0;
    }

    private void tickCloseGui() {
        if (!ready()) return;

        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();

        state = State.LOOP_WAIT;
        ticker = loopDelay.get();
    }

    private void tickLoopWait() {
        if (!ready()) return;
        resetCycle();
        state = State.IDLE;
    }

    // -- Ket qua ban hang ----------------------------------------------------

    private void onSold() {
        sellBatchesDone++;
        announce("§aĐã đăng lượt §f" + sellBatchesDone + "/" + sellBatches.get()
            + "§a (" + itemsText() + ") giá §f" + sellPrice.get());

        // Con luot ban -> mo lai sell-command ngay (het item thi PLACE_ITEMS tu chuyen sang pha huy).
        if (sellBatchesDone < sellBatches.get()) {
            state = State.IDLE;
            ticker = stepDelay.get();
            return;
        }

        startCancelPhase();
    }

    /** Bat dau pha huy: doi het thoi gian roi mo /ah. Neu tat cancel-listing thi ket thuc vong luon. */
    private void startCancelPhase() {
        if (!cancelEnabled.get()) {
            finishCycle(true);
            return;
        }

        cancelledCount = 0;
        cancelClicks = 0;
        retries = 0;
        cancelTicker = cancelWaitTicks();
        lastAnnouncedMinute = -1;

        if (cancelTicker > 0) {
            announce("§eSẽ mở /ah và huỷ listing sau §b" + formatTicks(cancelTicker) + "§e.");
        }

        state = State.CANCEL_WAIT;
    }

    /** Loi trong pha ban: da dang duoc luot nao thi huy luon cac listing do, chua thi thu lai vong. */
    private void sellFailed(String reason) {
        announce("§c" + reason);

        if (sellBatchesDone > 0) {
            closeGui();
            startCancelPhase();
        } else {
            retryCycle();
        }
    }

    /** Ket thuc pha huy (da huy het, het listing, hoac gap loi). */
    private void finishCancel() {
        if (cancelledCount > 0) announce("§aĐã huỷ tổng cộng §f" + cancelledCount + "§a listing");
        // Chua huy duoc cai nao -> giu danh sach item de vong sau thu huy lai.
        finishCycle(cancelledCount > 0);
    }

    private void finishCycle(boolean clearSold) {
        cycles++;
        if (clearSold) soldItems.clear();
        state = State.CLOSE_GUI;
        ticker = stepDelay.get();
    }

    private int cancelWaitTicks() {
        return Math.max(0, cancelWaitMinutes.get()) * 1200 + Math.max(0, cancelWaitSeconds.get()) * 20;
    }

    private static String formatTicks(int ticks) {
        int totalSeconds = (ticks + 19) / 20;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        if (minutes == 0) return seconds + " giây";
        if (seconds == 0) return minutes + " phút";
        return minutes + " phút " + seconds + " giây";
    }

    private void closeGui() {
        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();
    }

    // -- Helpers -------------------------------------------------------------

    /** Dem nguoc ticker, tra ve true khi duoc phep chay buoc tiep. */
    private boolean ready() {
        if (ticker > 0) {
            ticker--;
            return false;
        }
        return true;
    }

    private int minutesLeft() {
        return (cancelTicker + 1199) / 1200;
    }

    private void announce(String message) {
        if (!chatAnnounce.get()) return;
        info(message);
    }

    private void retryCycle() {
        if (mc.player != null && mc.currentScreen != null) mc.player.closeHandledScreen();
        resetCycle();
        state = State.LOOP_WAIT;
        ticker = loopDelay.get();
    }

    private void retryCycle(String reason) {
        announce("§c" + reason);
        retryCycle();
    }

    private void sendCommand(String command) {
        if (command == null || command.isEmpty()) return;
        ChatUtils.sendPlayerMsg(command);
        AhSellAddon.LOG.info("[AutoAhPlus] {}", command);
    }

    private String buildSellCommand() {
        String base = trimSlash(sellCommand.get());
        if (base.isEmpty()) {
            announce("§csell-command đang trống");
            retryCycle();
            return null;
        }

        String price = sellPrice.get() == null ? "" : sellPrice.get().trim();
        return price.isEmpty() ? "/" + base : "/" + base + " " + price;
    }

    private static String commandOf(String raw) {
        String base = trimSlash(raw);
        return "/" + base;
    }

    private static String trimSlash(String raw) {
        if (raw == null) return "";
        String value = raw.trim();
        while (value.startsWith("/")) value = value.substring(1).trim();
        return value;
    }

    private void parseTargets() {
        targets.clear();
        String raw = sellSlots.get();

        if (raw != null) {
            for (String part : raw.split("[,;\\s]+")) {
                if (part.isEmpty()) continue;
                try {
                    int slot = Integer.parseInt(part.trim());
                    if (slot >= 0 && slot < 54) targets.add(slot);
                } catch (NumberFormatException ignored) {
                    // bo qua gia tri rac
                }
            }
        }

        if (targets.isEmpty()) targets.add(13);
    }

    private ScreenHandler container() {
        if (!(mc.currentScreen instanceof HandledScreen<?> screen)) return null;
        ScreenHandler handler = screen.getScreenHandler();
        if (handler == null) return null;
        if (handler.slots.size() < 36) return null;
        return handler;
    }

    private static int playerStart(ScreenHandler handler) {
        return handler.slots.size() - 36;
    }

    private static int containerSize(ScreenHandler handler) {
        int total = handler.slots.size();
        if (handler instanceof GenericContainerScreenHandler chest) {
            return Math.min(chest.getRows() * 9, Math.max(0, total - 36));
        }
        return Math.max(0, total - 36);
    }

    private static boolean inContainer(ScreenHandler handler, int slot) {
        return slot >= 0 && slot < containerSize(handler);
    }

    private void click(ScreenHandler handler, int slot, int button, SlotActionType type) {
        if (handler == null || mc.player == null || mc.interactionManager == null) return;
        if (slot < 0 || slot >= handler.slots.size()) return;
        mc.interactionManager.clickSlot(handler.syncId, slot, button, type, mc.player);
    }

    // -- Nhan dien item / slot -----------------------------------------------

    /** Slot trong tui co item hop le de ban. */
    private int findSourceSlot(ScreenHandler handler) {
        int start = playerStart(handler);
        for (int i = start; i < handler.slots.size(); i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (!sellable(stack)) continue;
            return i;
        }
        return -1;
    }

    private boolean sellable(ItemStack stack) {
        if (stack.isEmpty()) return false;

        String filter = itemFilter.get();
        if (filter == null || filter.trim().isEmpty()) return true;

        String id = normalize(Registries.ITEM.getId(stack.getItem()).getPath());
        String name = normalize(stack.getName().getString());

        for (String part : filter.split(",")) {
            String keyword = normalize(part);
            if (keyword.isEmpty()) continue;
            if (id.contains(keyword) || name.contains(keyword)) return true;
        }

        return false;
    }

    /** Nut xac nhan mau xanh. */
    private int findConfirmSlot(ScreenHandler handler) {
        int limit = containerSize(handler);

        int configured = confirmSlot.get();
        if (configured >= 0 && configured < limit) {
            if (!handler.slots.get(configured).getStack().isEmpty()) return configured;
        }

        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (!stack.isEmpty() && GREEN_ITEMS.contains(stack.getItem())) return i;
        }

        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;
            String name = normalize(stack.getName().getString());
            if (name.contains("xac nhan") || name.contains("ban") || name.contains("confirm") || name.contains("sell")) {
                return i;
            }
        }

        return -1;
    }

    /** Nut "item cua ban" (ender chest). */
    private int findMyItemSlot(ScreenHandler handler) {
        int limit = containerSize(handler);

        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (!stack.isEmpty() && stack.getItem() == Items.ENDER_CHEST) return i;
        }

        String wanted = normalize(myItemName.get());
        if (!wanted.isEmpty()) {
            for (int i = 0; i < limit; i++) {
                ItemStack stack = handler.slots.get(i).getStack();
                if (stack.isEmpty()) continue;
                if (normalize(stack.getName().getString()).contains(wanted)) return i;
            }
        }

        return -1;
    }

    /** Mon do vua ban trong danh sach listing. */
    private int findListingSlot(ScreenHandler handler) {
        int limit = containerSize(handler);

        int configured = listingSlot.get();
        if (configured >= 0 && configured < limit && !handler.slots.get(configured).getStack().isEmpty()) {
            return configured;
        }

        // Bat ky listing nao thuoc cac loai item da dang.
        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (!stack.isEmpty() && soldItems.contains(stack.getItem())) return i;
        }

        // Chi lan huy dau tien moi duoc "doan" bat ky mon nao; cac lan sau khong thay
        // listing khop nghia la da het, khong doan nua de tranh bam nham/lap vo han.
        if (cancelledCount > 0) return -1;

        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty() || isDecoration(stack)) continue;
            return i;
        }

        return -1;
    }

    /** Nut huy listing. */
    private int findCancelSlot(ScreenHandler handler) {
        int limit = containerSize(handler);

        int configured = cancelSlot.get();
        if (configured >= 0 && configured < limit && !handler.slots.get(configured).getStack().isEmpty()) {
            return configured;
        }

        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (!stack.isEmpty() && RED_ITEMS.contains(stack.getItem())) return i;
        }

        String wanted = normalize(cancelKeyword());
        for (int i = 0; i < limit; i++) {
            ItemStack stack = handler.slots.get(i).getStack();
            if (stack.isEmpty()) continue;
            String name = normalize(stack.getName().getString());
            if (name.contains(wanted) || name.contains("cancel") || name.contains("huy")) return i;
        }

        return -1;
    }

    private String cancelKeyword() {
        return normalize("huy");
    }

    private static boolean isDecoration(ItemStack stack) {
        Item item = stack.getItem();
        return item == Items.BARRIER
            || item == Items.ARROW
            || item == Items.PAPER
            || item == Items.BOOK
            || item == Items.KNOWLEDGE_BOOK
            || item == Items.ENDER_CHEST
            || item == Items.CHEST
            || item == Items.HOPPER
            || item == Items.COMPASS
            || item == Items.NETHER_STAR
            || item == Items.GRAY_STAINED_GLASS_PANE
            || item == Items.BLACK_STAINED_GLASS_PANE
            || item == Items.WHITE_STAINED_GLASS_PANE
            || item == Items.RED_STAINED_GLASS_PANE
            || item == Items.LIME_STAINED_GLASS_PANE
            || item == Items.GREEN_STAINED_GLASS_PANE;
    }

    /** Ten cac loai item da dang, dung de thong bao len chat. */
    private String itemsText() {
        if (soldItems.isEmpty()) return "item";

        List<String> names = new ArrayList<>();
        for (Item item : soldItems) names.add(new ItemStack(item).getName().getString());
        return String.join(", ", names);
    }

    /** Lowercase, bo ma mau, bo dau tieng Viet. */
    private static String normalize(String raw) {
        if (raw == null) return "";

        String text = raw.replaceAll("\u00a7[0-9a-fk-orA-FK-OR]", "");
        text = Normalizer.normalize(text, Normalizer.Form.NFD);
        text = text.replaceAll("\\p{M}+", "");
        text = text.replace('\u0111', 'd').replace('\u0110', 'D');

        return text.toLowerCase().trim();
    }
}
