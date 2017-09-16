/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package client.messages.commands;

import client.MapleClient;
import tools.FileoutputUtil;
import tools.StringUtil;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Alex
 */
public class CommandScriptManager {

    private static final ScriptEngineManager sem = new ScriptEngineManager();
    private static final CommandScriptManager instance = new CommandScriptManager();
    private final Map<MapleClient, CommandScriptManager> cms = new WeakHashMap<>();

    private static final String SCRIPT_PATH = "scripts/commands/";

    public static CommandScriptManager getInstance() {
        return instance;
    }

    public static Invocable getInvocableCommand(String commandType, String command, MapleClient c) {

        String path = SCRIPT_PATH + commandType + "/" + command + ".js";

        if (command.equals("nocommand"))
            path = "scripts/commands/nocommand.js";
        else
            path = "scripts/commands/" + commandType + "/" + path + ".js";

        ScriptEngine engine = null;

        if (c != null) {
            engine = c.getScriptEngine(path);
        }

        if (engine == null) {
            File scriptFile = new File(path);
            if (!scriptFile.exists()) {
                return null;
            }
            engine = sem.getEngineByName("nashorn");
            if (c != null) {
                c.setScriptEngine(path, engine);
            }
            try (Stream<String> stream = Files.lines(scriptFile.toPath(), Charset.forName(StringUtil.codeString(scriptFile)))) {
                String lines = "load('nashorn:mozilla_compat.js');" + stream.collect(Collectors.joining(System.lineSeparator()));
                engine.eval(lines);
                stream.close();
            } catch (ScriptException | IOException e) {
                System.err.println("Error executing script. Path: " + path + "\r\nException " + e);
                FileoutputUtil.log(FileoutputUtil.ScriptEx_Log, "Error executing script. Path: " + path + "\r\nException " + e);
                return null;
            }
        }
        return (Invocable) engine;
    }

    public void putCms(MapleClient c, CommandScriptManager acm) {
        cms.put(c, acm);
    }

    public final void dispose(final MapleClient c, final String commandType, final String command) {
        String path = SCRIPT_PATH;
        if (command.equals("nocommand"))
            path = "scripts/commands/nocommand.js";
        else
            path = "scripts/commands/" + commandType + "/" + path + ".js";

        final CommandScriptManager csm = cms.get(c);
        if (csm != null) {
            cms.remove(c);
            c.removeScriptEngine(path);
        }
        if (c.getPlayer() != null && c.getPlayer().getConversation() == 1) {
            c.getPlayer().setConversation(0);
        }
    }
}
