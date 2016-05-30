package com.ibericart.fuelanalyzer.config;

import com.github.pires.obd.commands.ObdCommand;
import com.github.pires.obd.commands.SpeedCommand;
import com.github.pires.obd.commands.control.DistanceMILOnCommand;
import com.github.pires.obd.commands.control.DtcNumberCommand;
import com.github.pires.obd.commands.control.EquivalentRatioCommand;
import com.github.pires.obd.commands.control.ModuleVoltageCommand;
import com.github.pires.obd.commands.control.TimingAdvanceCommand;
import com.github.pires.obd.commands.control.TroubleCodesCommand;
import com.github.pires.obd.commands.control.VinCommand;
import com.github.pires.obd.commands.engine.LoadCommand;
import com.github.pires.obd.commands.engine.MassAirFlowCommand;
import com.github.pires.obd.commands.engine.OilTempCommand;
import com.github.pires.obd.commands.engine.RPMCommand;
import com.github.pires.obd.commands.engine.RuntimeCommand;
import com.github.pires.obd.commands.engine.ThrottlePositionCommand;
import com.github.pires.obd.commands.fuel.AirFuelRatioCommand;
import com.github.pires.obd.commands.fuel.ConsumptionRateCommand;
import com.github.pires.obd.commands.fuel.FindFuelTypeCommand;
import com.github.pires.obd.commands.fuel.FuelLevelCommand;
import com.github.pires.obd.commands.fuel.FuelTrimCommand;
import com.github.pires.obd.commands.fuel.WidebandAirFuelRatioCommand;
import com.github.pires.obd.commands.pressure.BarometricPressureCommand;
import com.github.pires.obd.commands.pressure.FuelPressureCommand;
import com.github.pires.obd.commands.pressure.FuelRailPressureCommand;
import com.github.pires.obd.commands.pressure.IntakeManifoldPressureCommand;
import com.github.pires.obd.commands.temperature.AirIntakeTemperatureCommand;
import com.github.pires.obd.commands.temperature.AmbientAirTemperatureCommand;
import com.github.pires.obd.commands.temperature.EngineCoolantTemperatureCommand;
import com.github.pires.obd.enums.FuelTrim;

import java.util.ArrayList;

/**
 * OBD (On-Board Diagnostics) Configuration
 */
public final class ObdConfig {

    public static ArrayList<ObdCommand> getCommands() {
        ArrayList<ObdCommand> commands = new ArrayList<>();

        // control
        commands.add(new ModuleVoltageCommand());
        commands.add(new EquivalentRatioCommand());
        commands.add(new DistanceMILOnCommand());
        commands.add(new DtcNumberCommand());
        commands.add(new TimingAdvanceCommand());
        commands.add(new TroubleCodesCommand());
        commands.add(new VinCommand());

        // engine
        commands.add(new LoadCommand());
        commands.add(new RPMCommand());
        commands.add(new RuntimeCommand());
        commands.add(new MassAirFlowCommand());
        commands.add(new ThrottlePositionCommand());

        // fuel
        commands.add(new FindFuelTypeCommand());
        commands.add(new ConsumptionRateCommand());
        // commands.add(new AverageFuelEconomyObdCommand());
        //commands.add(new FuelEconomyCommand());
        commands.add(new FuelLevelCommand());
        // commands.add(new FuelEconomyMAPObdCommand());
        // commands.add(new FuelEconomyCommandedMAPObdCommand());
        commands.add(new FuelTrimCommand(FuelTrim.LONG_TERM_BANK_1));
        commands.add(new FuelTrimCommand(FuelTrim.LONG_TERM_BANK_2));
        commands.add(new FuelTrimCommand(FuelTrim.SHORT_TERM_BANK_1));
        commands.add(new FuelTrimCommand(FuelTrim.SHORT_TERM_BANK_2));
        commands.add(new AirFuelRatioCommand());
        commands.add(new WidebandAirFuelRatioCommand());
        commands.add(new OilTempCommand());

        // pressure
        commands.add(new BarometricPressureCommand());
        commands.add(new FuelPressureCommand());
        commands.add(new FuelRailPressureCommand());
        commands.add(new IntakeManifoldPressureCommand());

        // temperature
        commands.add(new AirIntakeTemperatureCommand());
        commands.add(new AmbientAirTemperatureCommand());
        commands.add(new EngineCoolantTemperatureCommand());

        // misc
        commands.add(new SpeedCommand());

        return commands;
    }
}
