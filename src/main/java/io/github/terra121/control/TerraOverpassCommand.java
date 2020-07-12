package io.github.terra121.control;

import io.github.terra121.TerraConfig;
import io.github.terra121.dataset.OpenStreetMaps;
import net.minecraft.client.resources.I18n;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextComponentString;

//TODO Tab completion
//TODO Permissions
//TODO Cancel tread
public class TerraOverpassCommand extends CommandBase {

	public static final String COMMAND_NAME = "overpass";
	
	@Override
	public String getName() {
		return COMMAND_NAME;
	}

	@Override
	public String getUsage(ICommandSender sender) {
		return "terra121.commands.overpass.usage";
	}

	@Override
	public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
		if(args.length != 1) throw new CommandException("terra121.commands.overpass.usage");
		String inUse = OpenStreetMaps.getOverpassEndpoint();
		boolean isDefault = inUse.equals(TerraConfig.serverOverpassDefault);
		boolean hasFallback = !TerraConfig.serverOverpassFallback.equals("");
		switch(args[0]) {
		case "status":
			String t = isDefault? "terra121.commands.overpass.status.default": "terra121.commands.overpass.status.fallback";
			ITextComponent msg = new TextComponentString(I18n.format(t, inUse));
			sender.sendMessage(msg);
			break;
		case "list":
			ITextComponent msg1 = new TextComponentString(I18n.format("terra121.commands.overpass.list.default", TerraConfig.serverOverpassDefault));
			ITextComponent msg2 = new TextComponentString(hasFallback?
					I18n.format("terra121.commands.overpass.list.fallback", TerraConfig.serverOverpassFallback):
					I18n.format("terra121.commands.overpass.list.nofallback"));
			sender.sendMessage(msg1);
			sender.sendMessage(msg2);
			break;
		case "default":
			OpenStreetMaps.setOverpassEndpoint(TerraConfig.serverOverpassDefault);
			msg = new TextComponentString(I18n.format("terra121.commands.overpass.set.default"));
			sender.sendMessage(msg);
			break;
		case "fallback":
			if(!hasFallback) throw new CommandException(I18n.format("terra121.commands.overpass.list.nofallback"));
			OpenStreetMaps.setOverpassEndpoint(TerraConfig.serverOverpassFallback);
			msg = new TextComponentString(I18n.format("terra121.commands.overpass.set.fallback"));
			sender.sendMessage(msg);
			break;
		default:
			throw new CommandException("terra121.commands.overpass.usage");
		}
	}

}
