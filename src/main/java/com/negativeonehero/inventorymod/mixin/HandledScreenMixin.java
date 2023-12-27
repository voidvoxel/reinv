package com.negativeonehero.inventorymod.mixin;

import com.negativeonehero.inventorymod.SortingType;
import com.negativeonehero.inventorymod.impl.IPlayerInventory;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemGroup;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(HandledScreen.class)
public abstract class HandledScreenMixin<T extends ScreenHandler> extends Screen {
    @Unique
    private PlayerInventory inventory;
    @Unique
    private IPlayerInventory iPlayerInventory;
    @Unique
    private ButtonWidget previousButton;
    @Unique
    private ButtonWidget nextButton;
    @Unique
    private ButtonWidget functionButton;
    @Unique
    private ButtonWidget sortingTypeButton;
    @Unique
    private boolean sorting = false;
    @Unique
    private int page = 1;
    @Unique
    private SortingType sortingType = SortingType.COUNT;
    @Unique
    private int ticksSinceSorting = 0;

    @Unique
    private Text previousTooltip = Text.of("");
    @Unique
    private Text nextTooltip = Text.of("");

    protected HandledScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "<init>", at = @At(value = "TAIL"))
    public void constructor(T handler, PlayerInventory inventory, Text title, CallbackInfo ci) {
        this.inventory = inventory;
        this.iPlayerInventory = (IPlayerInventory) inventory;
    }

    @Inject(method = "init", at = @At(value = "TAIL"))
    public void init(CallbackInfo ci) {
        this.previousButton = new ButtonWidget(10, 10, 16, 16, Text.of("<"),
                button -> this.update(false), (button, matrices, mouseX, mouseY) -> {
            if (mouseX >= button.x && mouseX <= button.x+button.getWidth()
                    && mouseY >= button.y && mouseY <= button.y+button.getHeight())
                this.renderTooltip(matrices, this.previousTooltip, mouseX, mouseY);
                });
        this.addDrawableChild(this.previousButton);
        this.nextButton = new ButtonWidget(86, 10, 16, 16, Text.of(">"),
                button -> this.update(true), (button, matrices, mouseX, mouseY) -> {
            if (mouseX >= button.x && mouseX <= button.x+button.getWidth()
                    && mouseY >= button.y && mouseY <= button.y+button.getHeight())
                this.renderTooltip(matrices, this.nextTooltip, mouseX, mouseY);
        });
        this.addDrawableChild(this.nextButton);
        this.functionButton = new ButtonWidget(26, 10, 60, 16, Text.of("Page " + page),
                button -> {
                    this.sorting = !this.sorting;
                    this.updateTooltip();
                });
        this.addDrawableChild(this.functionButton);
        this.sortingTypeButton = new ButtonWidget(10, 26, 92, 16, this.sortingType.message,
                button -> {
                    this.sortingType = this.sortingType.next();
                    button.setMessage(this.sortingType.message);
                });
        this.addDrawableChild(this.sortingTypeButton);
        this.updateTooltip();
    }

    @Unique
    private void update(boolean next) {
        if (sorting) {
            this.iPlayerInventory.sort(next, this.page, this.sortingType);
            this.previousButton.active = false;
            this.nextButton.active = false;
        } else {
            if(next) {
                if (this.page > 1) this.iPlayerInventory.swapInventory(this.page);
                this.page++;
                this.previousButton.visible = true;
                if (this.page >= this.inventory.size() / 27) this.nextButton.visible = false;
                this.iPlayerInventory.swapInventory(this.page);
            } else {
                this.iPlayerInventory.swapInventory(this.page);
                this.page--;
                if (this.page < this.inventory.size() / 27) {
                    this.nextButton.visible = true;
                    if (this.page <= 1) this.previousButton.visible = false;
                    else this.iPlayerInventory.swapInventory(this.page);
                }
            }
            this.updateTooltip();
        }
    }

    @Unique
    private void updateTooltip() {
        if(sorting) {
            this.previousTooltip = Text.of("Sort Descending");
            this.nextTooltip = Text.of("Sort Ascending");
        } else {
            this.previousTooltip = Text.of("Page " + (page - 1));
            this.nextTooltip = Text.of("Page " + (page + 1));
        }
    }

    @SuppressWarnings("ConstantValue")
    @Unique
    private void updateButtons() {
        boolean visible = (((Object) this) instanceof CreativeInventoryScreen
                && CreativeInventoryScreen.selectedTab == ItemGroup.INVENTORY.getIndex())
                || ((Object) this) instanceof InventoryScreen;
        if(sorting) {
            this.previousButton.visible = visible;
            this.functionButton.visible = visible;
            this.nextButton.visible = visible;
            this.sortingTypeButton.visible = visible;
        } else {
            this.previousButton.visible = this.page > 1 && visible;
            this.functionButton.visible = visible;
            this.nextButton.visible = this.page < this.inventory.size() / 27 && visible;
            this.sortingTypeButton.visible = false;
        }
    }

    @Inject(method = "removed", at = @At(value = "HEAD"))
    public void resetInventory(CallbackInfo ci) {
        this.iPlayerInventory.swapInventory(this.page);
    }

    @Inject(method = "render", at = @At(value = "HEAD"))
    public void updateButtonsVisibility(MatrixStack matrices, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        this.updateButtons();
    }

    @Inject(method = "tick", at = @At(value = "HEAD"))
    public void tick(CallbackInfo ci) {
        if(!this.sorting && !this.nextButton.visible && 27 * page + 14 < this.inventory.size()) {
            this.nextButton.visible = true;
        }
        if(!this.previousButton.active) {
            if(this.ticksSinceSorting >= 20 || !this.sorting) {
                this.ticksSinceSorting = 0;
                this.previousButton.active = true;
                this.nextButton.active = true;
            } else {
                this.ticksSinceSorting++;
            }
        }
        if(sorting) {
            this.functionButton.setMessage(Text.of("Sorting"));
        } else {
            this.functionButton.setMessage(Text.of("Page " + page + "/" + (this.inventory.size() - 14) / 27));
        }
    }
}
