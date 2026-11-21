package yogurt.ahsell;

import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import org.slf4j.Logger;
import yogurt.ahsell.module.AutoAhPlus;

public class AhSellAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("AH Sell", new ItemStack(Items.GOLD_INGOT));

    @Override
    public void onInitialize() {
        LOG.info("AH Sell addon loaded (author: yogurt)");
        Modules.get().add(new AutoAhPlus());
    }

    @Override
    public void onRegisterCategories() {
        // Modules.get().add() throws if the module's category was never registered.
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "yogurt.ahsell";
    }
}
