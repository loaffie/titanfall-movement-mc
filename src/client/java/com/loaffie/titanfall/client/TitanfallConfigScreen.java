package com.loaffie.titanfall.client;

import com.loaffie.titanfall.config.TitanfallConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;

/**
 * Config screen for the movement model. The layout adapts to the window: it prefers two columns and
 * adds a third, and squeezes the row height, when the window is too short or too narrow for that, so
 * the screen still fits at high GUI scale or in a small window instead of running off the bottom.
 */
public class TitanfallConfigScreen extends Screen {
	private static final int MAX_ROW_HEIGHT = 20;
	private static final int MIN_ROW_HEIGHT = 12;
	private static final int TOP = 22;
	private static final int GAP = 8;

	private final Screen parent;
	private final TitanfallConfig config = TitanfallConfig.get();
	private final List<Row> rows = new ArrayList<>();

	public TitanfallConfigScreen(Screen parent) {
		super(Text.literal("Titanfall Movement"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		rows.clear();
		toggle("Require Jumpkit", () -> config.requireJumpkit, () -> config.requireJumpkit = !config.requireJumpkit);
		toggle("No Fall Damage", () -> config.jumpkitNegatesFallDamage, () -> config.jumpkitNegatesFallDamage = !config.jumpkitNegatesFallDamage);
		toggle("Thruster FX", () -> config.thrusterParticles, () -> config.thrusterParticles = !config.thrusterParticles);
		slider("Air Speed Cap", 0.0, 6.0, config.airSpeedCap, value -> config.airSpeedCap = value);
		slider("Air Accel", 0.0, 60.0, config.airAcceleration, value -> config.airAcceleration = value);
		whole("Air Substeps", 1.0, 6.0, config.airSubsteps, value -> config.airSubsteps = (int) value);
		slider("Max Velocity", 10.0, 90.0, config.maxVelocity, value -> config.maxVelocity = value);
		slider("Sprint Takeoff", 4.0, 12.0, config.sprintTakeoffSpeed, value -> config.sprintTakeoffSpeed = value);
		slider("Double Jump", 0.0, 0.8, config.doubleJumpVelocity, value -> config.doubleJumpVelocity = value);
		slider("DJ Boost", 0.0, 5.0, config.doubleJumpBoost, value -> config.doubleJumpBoost = value);
		slider("DJ Redirect", 0.0, 1.0, config.doubleJumpRedirect, value -> config.doubleJumpRedirect = value);
		whole("Lurch Ticks", 0.0, 20.0, config.lurchTicks, value -> config.lurchTicks = (int) value);
		slider("Lurch Strength", 0.0, 1.0, config.lurchStrength, value -> config.lurchStrength = value);
		slider("Lurch Max Loss", 0.0, 0.5, config.lurchMaxSpeedLoss, value -> config.lurchMaxSpeedLoss = value);
		slider("Ground Friction", 0.0, 10.0, config.groundFriction, value -> config.groundFriction = value);
		slider("Skid Release", 4.0, 8.0, config.skidReleaseSpeed, value -> config.skidReleaseSpeed = value);
		slider("Slide Min Speed", 3.0, 8.0, config.slideMinSpeed, value -> config.slideMinSpeed = value);
		slider("Slide Boost", 0.0, 8.0, config.slideBoost, value -> config.slideBoost = value);
		slider("Slide Boost Cap", 5.0, 20.0, config.slideBoostCap, value -> config.slideBoostCap = value);
		whole("Slide Cooldown", 0.0, 100.0, config.slideBoostCooldownTicks, value -> config.slideBoostCooldownTicks = (int) value);
		slider("Slide Decel", 0.0, 5.0, config.slideDecel, value -> config.slideDecel = value);
		slider("Slide Decay/s", 0.3, 1.0, config.slideDecay, value -> config.slideDecay = value);
		slider("Slide Steer", 0.0, 15.0, config.slideSteer, value -> config.slideSteer = value);
		slider("Slide Gap Speed", 0.0, 8.0, config.slideCeilingSpeed, value -> config.slideCeilingSpeed = value);
		slider("Wall Min Speed", 2.0, 10.0, config.wallRunMinSpeed, value -> config.wallRunMinSpeed = value);
		whole("Wall Run Ticks", 5.0, 100.0, config.wallRunMaxTicks, value -> config.wallRunMaxTicks = (int) value);
		slider("Wall Max Speed", 5.0, 25.0, config.wallRunMaxSpeed, value -> config.wallRunMaxSpeed = value);
		slider("Wall Accel", 0.0, 60.0, config.wallRunAccel, value -> config.wallRunAccel = value);
		slider("Wall Speed Keep", 0.5, 1.0, config.wallRunSpeedRetain, value -> config.wallRunSpeedRetain = value);
		slider("Wall Gravity", 0.0, 20.0, config.wallRunGravity, value -> config.wallRunGravity = value);
		slider("Wall V Damping", 0.0, 20.0, config.wallRunVerticalDamping, value -> config.wallRunVerticalDamping = value);
		slider("Wall Jump Push", 0.0, 12.0, config.wallJumpPush, value -> config.wallJumpPush = value);
		slider("Wall Jump Up", 0.0, 12.0, config.wallJumpUp, value -> config.wallJumpUp = value);
		slider("Wall Jump Input", 0.0, 6.0, config.wallJumpInputSpeed, value -> config.wallJumpInputSpeed = value);
		whole("Wall Cooldown", 0.0, 20.0, config.wallCooldownTicks, value -> config.wallCooldownTicks = (int) value);
		slider("Wall Entry Speed", 0.0, 10.0, config.wallRunEntrySpeed, value -> config.wallRunEntrySpeed = value);
		slider("Wall Reverse Acc", 0.0, 60.0, config.wallRunReverseAccel, value -> config.wallRunReverseAccel = value);
		slider("Wall Reach", 0.1, 0.8, config.wallRunReach, value -> config.wallRunReach = value);
		slider("Cam Wall Roll", -25.0, 25.0, config.wallRunCameraRoll, value -> config.wallRunCameraRoll = value);
		slider("Cam Jump Kick", 0.0, 15.0, config.wallJumpCameraKick, value -> config.wallJumpCameraKick = value);
		slider("Cam Slide Drop", 0.0, 0.6, config.slideCameraDrop, value -> config.slideCameraDrop = value);
		slider("Slide FOV", 0.0, 0.25, config.slideFovBoost, value -> config.slideFovBoost = value);
		slider("Cam Smoothing", 1.0, 30.0, config.cameraSmoothing, value -> config.cameraSmoothing = value);
		toggle("Animations", () -> config.animationsEnabled, () -> config.animationsEnabled = !config.animationsEnabled);
		slider("Wall Lean", 0.0, 40.0, config.wallRunLean, value -> config.wallRunLean = value);
		slider("Slide Recline", 0.0, 70.0, config.slideRecline, value -> config.slideRecline = value);
		toggle("Air Jump / Wall Run", () -> config.airJumpOnWallRun, () -> config.airJumpOnWallRun = !config.airJumpOnWallRun);
		toggle("Air Jump / Wall Bump", () -> config.airJumpOnWallBump, () -> config.airJumpOnWallBump = !config.airJumpOnWallBump);
		slider("Grapple Range", 8.0, 64.0, config.grappleRange, value -> config.grappleRange = value);
		slider("Grapple Pull", 4.0, 25.0, config.grapplePullSpeed, value -> config.grapplePullSpeed = value);
		slider("Grapple Pull Far", 4.0, 25.0, config.grapplePullSpeedFar, value -> config.grapplePullSpeedFar = value);
		slider("Grapple Accel", 5.0, 80.0, config.grapplePullAccel, value -> config.grapplePullAccel = value);
		whole("Grapple Charges", 1.0, 5.0, config.grappleCharges, value -> config.grappleCharges = (int) value);
		slider("Grapple Cooldown", 0.0, 30.0, config.grappleCooldownSeconds, value -> config.grappleCooldownSeconds = value);
		toggle("Tactical HUD", () -> config.grappleHud, () -> config.grappleHud = !config.grappleHud);
		toggle("No Items On Line", () -> config.grappleBlocksItemUse, () -> config.grappleBlocksItemUse = !config.grappleBlocksItemUse);
		slider("Stim Length", 1.0, 10.0, config.stimLengthSeconds, value -> config.stimLengthSeconds = value);
		slider("Stim Heal After", 0.0, 5.0, config.stimHealLingerSeconds, value -> config.stimHealLingerSeconds = value);
		slider("Stim Speed", 0.0, 1.0, config.stimSpeedBoost, value -> config.stimSpeedBoost = value);
		slider("Stim Cooldown", 0.0, 60.0, config.stimCooldownSeconds, value -> config.stimCooldownSeconds = value);
		slider("Stim Heal/s", 0.0, 2.0, config.stimHealPerSecond, value -> config.stimHealPerSecond = value);
		toggle("Stim Screen FX", () -> config.stimScreenEffects, () -> config.stimScreenEffects = !config.stimScreenEffects);
		slider("Phase Length", 0.5, 5.0, config.phaseLengthSeconds, value -> config.phaseLengthSeconds = value);
		slider("Phase Cooldown", 0.0, 60.0, config.phaseCooldownSeconds, value -> config.phaseCooldownSeconds = value);
		toggle("Phase Telefrag", () -> config.phaseTelefrag, () -> config.phaseTelefrag = !config.phaseTelefrag);
		toggle("Phase Screen FX", () -> config.phaseScreenEffects, () -> config.phaseScreenEffects = !config.phaseScreenEffects);
		slider("Pulse Cooldown", 0.0, 60.0, config.pulseCooldownSeconds, value -> config.pulseCooldownSeconds = value);
		slider("Pulse Radius", 8.0, 64.0, config.pulseRadius, value -> config.pulseRadius = value);
		slider("Pulse Throw Speed", 20.0, 120.0, config.pulseThrowSpeed, value -> config.pulseThrowSpeed = value);
		slider("Pulse Hit Damage", 0.0, 40.0, config.pulseImpactDamage, value -> config.pulseImpactDamage = value);
		toggle("Pulse Aim Arc", () -> config.pulseArc, () -> config.pulseArc = !config.pulseArc);
		toggle("Sounds", () -> config.soundsEnabled, () -> config.soundsEnabled = !config.soundsEnabled);
		slider("Volume", 0.0, 1.0, config.movementVolume, value -> config.movementVolume = value);

		int gridBottom = this.height - 27 - 6;
		int columns = 2;
		while (columns < 6 && TOP + perColumn(columns) * MAX_ROW_HEIGHT > gridBottom) {
			columns++;
		}

		int perColumn = perColumn(columns);
		int rowHeight = Math.max(MIN_ROW_HEIGHT, Math.min(MAX_ROW_HEIGHT, Math.max(1, (gridBottom - TOP) / perColumn)));
		int widgetHeight = Math.max(MIN_ROW_HEIGHT, rowHeight - 2);
		int columnWidth = Math.min(170, Math.max(70, (this.width - 20) / columns - GAP));
		int gridWidth = columnWidth * columns + GAP * (columns - 1);
		int startX = Math.max(5, (this.width - gridWidth) / 2);

		for (int index = 0; index < rows.size(); index++) {
			Row row = rows.get(index);
			int x = startX + (index / perColumn) * (columnWidth + GAP);
			int y = TOP + (index % perColumn) * rowHeight;
			addDrawableChild(widget(row, x, y, columnWidth, widgetHeight));
		}

		addDrawableChild(ButtonWidget.builder(Text.literal("Done"), button -> close()).dimensions(this.width / 2 - 50, this.height - 27, 100, 20).build());
	}

	@Override
	public void close() {
		config.save();
		MinecraftClient.getInstance().setScreen(parent);
	}

	private ClickableWidget widget(Row row, int x, int y, int width, int height) {
		if (row.kind() == Kind.TOGGLE) {
			return ButtonWidget.builder(toggleLabel(row), button -> {
				row.toggle().run();
				button.setMessage(toggleLabel(row));
			}).dimensions(x, y, width, height).build();
		}

		return new ConfigSlider(x, y, width, height, row);
	}

	private Text toggleLabel(Row row) {
		return Text.literal(row.label() + ": " + (row.getter().getAsBoolean() ? "ON" : "OFF"));
	}

	private int perColumn(int columns) {
		return (rows.size() + columns - 1) / columns;
	}

	private void slider(String label, double min, double max, double value, DoubleConsumer setter) {
		rows.add(new Row(label, min, max, value, Kind.SLIDER, setter, null, null));
	}

	private void whole(String label, double min, double max, double value, DoubleConsumer setter) {
		rows.add(new Row(label, min, max, value, Kind.WHOLE, setter, null, null));
	}

	private void toggle(String label, BooleanSupplier getter, Runnable toggle) {
		rows.add(new Row(label, 0.0, 1.0, 0.0, Kind.TOGGLE, null, getter, toggle));
	}

	private enum Kind {
		SLIDER,
		WHOLE,
		TOGGLE
	}

	private record Row(String label, double min, double max, double value, Kind kind, DoubleConsumer setter, BooleanSupplier getter, Runnable toggle) {
	}

	private static class ConfigSlider extends SliderWidget {
		private final Row row;

		ConfigSlider(int x, int y, int width, int height, Row row) {
			super(x, y, width, height, Text.literal(row.label()), (row.value() - row.min()) / (row.max() - row.min()));
			this.row = row;
			updateMessage();
		}

		@Override
		protected void updateMessage() {
			double current = current();
			String text = row.kind() == Kind.WHOLE ? Integer.toString((int) Math.round(current)) : String.format(Locale.ROOT, "%.2f", current);
			setMessage(Text.literal(row.label() + ": " + text));
		}

		@Override
		protected void applyValue() {
			double current = current();
			row.setter().accept(row.kind() == Kind.WHOLE ? Math.round(current) : current);
		}

		private double current() {
			return row.min() + value * (row.max() - row.min());
		}
	}
}
