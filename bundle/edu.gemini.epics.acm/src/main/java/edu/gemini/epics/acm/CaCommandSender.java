package edu.gemini.epics.acm;

import gov.aps.jca.TimeoutException;

import java.util.Set;

/**
 * @author jluhrs
 *
 */
public interface CaCommandSender {
    /**
     * Retrieves the name of this command sender.
     * 
     * @return name of this command sender.
     */
    public String getName();

    /**
     * Retrieves the description for this Command Sender
     * 
     * @return the description of this Command Sender
     */
    public String getDescription();

    /**
     * Retrieves the names of the parameters of this command sender.
     * 
     * @return set of parameter names.
     */
    public Set<String> getInfo();

    /**
     * Marks this command for execution. Useful for commands without parameters.
     */
    public void mark() throws TimeoutException;

    /**
     * Clears the MARK flag for this command for execution.
     */
    public void clear() throws TimeoutException;

    /**
     * Trigger the EPICS apply record. The command return immediately.
     * 
     * @return an object that implements <code>CaCommandMonitor</code>, which
     *         can be used to monitor the command execution and retrieve its
     *         result.
     */
    public CaCommandMonitor post();

    /**
     * Trigger the EPICS apply record and waits until the command processing
     * completes.
     * 
     * @return an object that implements <code>CaCommandMonitor</code>, which
     *         can be used to monitor the command execution and retrieve its
     *         result.
     */
    public CaCommandMonitor postWait() throws InterruptedException;

    /**
     * Trigger the EPICS apply record. The command return immediately.
     * 
     * @param callback
     *            an object that implements <code>CaCommandListener</code>,
     *            which will be notified when the command execution state
     *            changes. The object will be used only for this execution of
     *            the command
     * 
     * @return an object that implements <code>CaCommandMonitor</code>, which
     *         can be used to monitor the command execution and retrieve its
     *         result.
     */
    public CaCommandMonitor postCallback(CaCommandListener callback);

    /**
     * Adds a parameter of type <code>Integer</code> to this command sender. If
     * the parameter already exist, the existing object is used. CaException is
     * thrown if the existing parameter is of a different type or uses a
     * different EPICS channel.
     * 
     * @param name
     *            the name of the parameter.
     * @param channel
     *            the full EPICS channel name for the parameter
     * @param description
     *            optional description for the parameter
     * @return the parameter
     * @throws CaException
     */
    public CaParameter<Integer> addInteger(String name, String channel)
            throws CaException;

    public CaParameter<Integer> addInteger(String name, String channel,
            String description) throws CaException;

    /**
     * Adds a parameter of type <code>Double</code> to this command sender. If
     * the parameter already exist, the existing object is used. CaException is
     * thrown if the existing parameter is of a different type or uses a
     * different EPICS channel.
     * 
     * @param name
     *            the name of the parameter.
     * @param channel
     *            the full EPICS channel name for the parameter
     * @param description
     *            optional description for the parameter
     * @return the parameter
     * @throws CaException
     */
    public CaParameter<Double> addDouble(String name, String channel)
            throws CaException;

    public CaParameter<Double> addDouble(String name, String channel,
            String description) throws CaException;

    /**
     * Adds a parameter of type <code>Float</code> to this command sender. If
     * the parameter already exist, the existing object is used. CaException is
     * thrown if the existing parameter is of a different type or uses a
     * different EPICS channel.
     * 
     * @param name
     *            the name of the parameter.
     * @param channel
     *            the full EPICS channel name for the parameter
     * @param description
     *            optional description for the parameter
     * @return the parameter
     * @throws CaException
     */
    public CaParameter<Float> addFloat(String name, String channel)
            throws CaException;

    public CaParameter<Float> addFloat(String name, String channel,
            String description) throws CaException;

    /**
     * Adds a parameter of type <code>String</code> to this command sender. If
     * the parameter already exist, the existing object is used. CaException is
     * thrown if the existing parameter is of a different type or uses a
     * different EPICS channel.
     * 
     * @param name
     *            the name of the parameter.
     * @param channel
     *            the full EPICS channel name for the parameter
     * @param description
     *            optional description for the parameter
     * @return the parameter
     * @throws CaException
     */
    public CaParameter<String> addString(String name, String channel)
            throws CaException;

    public CaParameter<String> addString(String name, String channel,
            String description) throws CaException;

    /**
     * Removes a parameter from this command sender (optional operation).
     * 
     * @param name
     *            the name of the parameter to remove.
     */
    public void remove(String name);

    /**
     * Retrieves an existing parameter of type <code>Integer</code>.
     * 
     * @param name
     *            the name of the parameter.
     * @return the parameter, or <code>null</code> if it does not exist or is of
     *         a different type.
     */
    public CaParameter<Integer> getInteger(String name);

    /**
     * Retrieves an existing parameter of type <code>Double</code>.
     * 
     * @param name
     *            the name of the parameter.
     * @return the parameter, or <code>null</code> if it does not exist or is of
     *         a different type.
     */
    public CaParameter<Double> getDouble(String name);

    /**
     * Retrieves an existing parameter of type <code>Float</code>.
     * 
     * @param name
     *            the name of the parameter.
     * @return the parameter, or <code>null</code> if it does not exist or is of
     *         a different type.
     */
    public CaParameter<Float> getFloat(String name);

    /**
     * Retrieves an existing parameter of type <code>String</code>.
     * 
     * @param name
     *            the name of the parameter.
     * @return the parameter, or <code>null</code> if it does not exist or is of
     *         a different type.
     */
    public CaParameter<String> getString(String name);

}
