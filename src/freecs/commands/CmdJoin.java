/**
 * Copyright (C) 2003  Manfred Andres
 * 
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * Created on 28.09.2003
 */

package freecs.commands;
import freecs.Server;
import freecs.interfaces.ICommand;
import freecs.interfaces.IGroupState;
import freecs.core.GroupManager;
import freecs.core.User;
import freecs.core.UserManager;
import freecs.content.MessageState;

/**
 * @author Manfred Andres
 *
 * freecs.commands
 */
public class CmdJoin extends AbstractCommand {
	private final String cmd= "/j";
	private final String version = "1.02";
	private static final ICommand selve=new CmdJoin();

	private CmdJoin () { }
	
	public static ICommand getInstance () {
		return selve;
	}
	   
    public Object instanceForSystem() {
        return checkVersion();
    }
    
    public String getCmd() {
        return cmd;
    }

	/**
     * @return the version
     */
    public String getVersion() {
        return version;
    }
    
    private CmdJoin checkVersion(){
        String freecsversion  = Server.getVersion().substring(4, 12);
        int v = new Integer(freecsversion).intValue();
        if (v >= 20100204){
            return this;
        }
        Server.log(this, "Wrong Chatversion", Server.MSG_ERROR, Server.LVL_MAJOR);
        return null;
    }

    public boolean execute (MessageState msgState, String param) {
		if (param.length () < 1) {
			msgState.msgTemplate="error.j.noArg";
			msgState.sender.sendMessage (msgState.mp);
			return false;
		}
        boolean usrIsOnline = false;
        User cd = UserManager.mgr.getUserByName("j");
        if (cd != null){
            usrIsOnline = true;
        } 
        if (usrIsOnline && !msgState.sender.containsConfirmAction(cmd)){
            msgState.msgTemplate = "error.j.confirm";
            msgState.param = param;
            msgState.sender.sendMessage (msgState.mp);
            msgState.sender.addConfirmAction(cmd);
            return false;
        }
        msgState.sender.removeConfirmAction(cmd);

        msgState.sourceGroup=msgState.sender.getGroup();
		String groupname;
		String topic = null;
		int pos = param.indexOf (":");
		if (pos > -1) { // joincommand issued with topic
			topic = param.substring (pos+1).trim ();
			groupname = param.substring (0, pos).trim ();
		} else {
			groupname = param;
		}
		if (groupname.length() <1 ) {
			msgState.msgTemplate="error.j.noArg";
			msgState.sender.sendMessage (msgState.mp);
			return false;
		}
		if (Server.srv.MAX_GROUPNAME_LENGTH > 0 
            && groupname.length() > Server.srv.MAX_GROUPNAME_LENGTH)
			groupname = groupname.substring (0,Server.srv.MAX_GROUPNAME_LENGTH);
        if (msgState.sourceGroup.getRawName ().equalsIgnoreCase (groupname)) {
            msgState.targetGroup=msgState.sourceGroup;
            msgState.msgTemplate="error.j.alreadyHere";
            msgState.sender.sendMessage (msgState.mp);
            return false;
        }
		msgState.sourceGroup = msgState.sender.getGroup ();
//        boolean senderWasSu = msgState.sourceGroup.usrIsSu(msgState.sender);
		msgState.targetGroup =  GroupManager.mgr.getGroup (groupname);
        if (!GroupManager.mgr.isStartingGroup(groupname)
            && isPunished (msgState))
                return false;
        if (msgState.sender.isPunished()) {
            if (msgState.targetGroup != null && msgState.targetGroup.usrMayJoinPunished(msgState.sender)){
                msgState.targetGroup.incrementJoinPunishedCounter();
            } else {
                msgState.msgTemplate = "error.user.punished";
                msgState.sender.sendMessage(msgState.mp);
        	    return false;
            }
        }
        if (!msgState.sender.isPunished())
            msgState.sourceGroup.resetJoinPunishedCounter();
		if (msgState.targetGroup != null 
            && msgState.targetGroup.usrIsBaned (msgState.sender.getName())) {
                msgState.msgTemplate = "error.j.banned";
                msgState.sender.sendMessage (msgState.mp);
                return false;
        }
		boolean created = false;
		if (msgState.targetGroup == null) {
            msgState.targetGroup = GroupManager.mgr.openGroup (groupname, topic, msgState.sender);
			if (msgState.targetGroup == null) {
			    if (sendReason(groupname, msgState)){
			        return false;
                } else {
			        msgState.param = groupname;
				    msgState.msgTemplate = "error.j.noRightToOpen";
				    msgState.sender.sendMessage (msgState.mp);
				    return false;
                }
			}
			created=true;
		} else if (!msgState.targetGroup.hasState(IGroupState.OPEN)) {
            msgState.msgTemplate = "error.j.closed";
            msgState.sender.sendMessage (msgState.mp);
            return false;
        } else if (msgState.sender.isUnregistered && msgState.targetGroup.hasState(IGroupState.NOT_ALLOW_JOIN_UNREG)) {
            msgState.param = groupname;
            msgState.msgTemplate = "error.j.joinunreg";
            msgState.sender.sendMessage (msgState.mp);
            return false;
        } else  if (isMembershipLocked(msgState, groupname)){
            return false;       
        } else if (!msgState.targetGroup.usrMayJoin(msgState.sender)) {
			msgState.msgTemplate = "error.j.noRight";
			msgState.sender.sendMessage (msgState.mp);
			return false;
        }
		
		
        if (!created) {
            msgState.msgTemplate = "message.user.join.group";
            msgState.targetGroup.sendMessage (msgState.mp);
            msgState.targetGroup.addUser (msgState.sender);
            msgState.msgTemplate = "message.j";
            msgState.sender.sendMessage (msgState.mp);
        } else {
            if (topic != null){
                msgState.targetGroup.setThemeCreator(msgState.sender);
            }
            msgState.msgTemplate = "message.j.created";
            msgState.sender.sendMessage(msgState.mp);
        }
        if (msgState.sourceGroup.size() > 0) {
            msgState.msgTemplate = "message.user.leaving.group";
            msgState.sourceGroup.sendMessage (msgState.mp);
        }
		return true;
	}
}
