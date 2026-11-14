package com.mahghuuuls.somepackdefaults.command;

import com.mahghuuuls.somepackdefaults.report.InspectionLines;
import com.mahghuuuls.somepackdefaults.report.StartReport;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * {@code /somepackdefaults inspect}: what this start did with every entry, and the current values.
 * Permission level 2, as vanilla's own operator commands: operators, the server console, and a
 * singleplayer host with cheats on. It changes nothing.
 */
public final class InspectCommand extends CommandBase {

    private static final String SUBCOMMAND = "inspect";

    private final Supplier<List<StartReport>> reports;

    public InspectCommand(Supplier<List<StartReport>> reports) {
        this.reports = reports;
    }

    @Override
    public String getName() {
        return "somepackdefaults";
    }

    @Override
    public String getUsage(ICommandSender sender) {
        return "/somepackdefaults inspect";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1 || !SUBCOMMAND.equals(args[0])) {
            throw new WrongUsageException(getUsage(sender));
        }
        for (String line : InspectionLines.lines(reports.get(), new GameValues(server))) {
            sender.sendMessage(new TextComponentString(line));
        }
    }

    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, BlockPos targetPos) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, SUBCOMMAND) : Collections.<String>emptyList();
    }
}
