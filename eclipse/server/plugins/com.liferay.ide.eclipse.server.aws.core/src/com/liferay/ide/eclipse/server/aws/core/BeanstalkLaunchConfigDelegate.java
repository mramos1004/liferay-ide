/**
 * Copyright (c) 2000-2011 Liferay, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Liferay Enterprise
 * Subscription License ("License"). You may not use this file except in
 * compliance with the License. You can obtain a copy of the License by
 * contacting Liferay, Inc. See the License for the specific language governing
 * permissions and limitations under the License, including but not limited to
 * distribution rights of the Software.
 */

package com.liferay.ide.eclipse.server.aws.core;

import java.util.Map;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.jdt.launching.AbstractJavaLaunchConfigurationDelegate;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMConnector;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.wst.server.core.IServer;
import org.eclipse.wst.server.core.ServerCore;

/**
 * @author Greg Amerson
 */
public class BeanstalkLaunchConfigDelegate extends AbstractJavaLaunchConfigurationDelegate {

	// public static final String HAS_WSADMIN_CONNECTION = "has-wsadmin-connection";

	public static final String SERVER_ID = "server-id";

	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
		throws CoreException {

		String serverId = configuration.getAttribute(SERVER_ID, "");
		IServer server = ServerCore.findServer(serverId);

		if (server == null) {
			// server has been deleted
			launch.terminate();
			return;
		}

		int state = server.getServerState();

		if (state != IServer.STATE_STARTED) {
			throw new CoreException(
				AWSCorePlugin.createErrorStatus("Server is not running. The WebSphere server adapter only supports connecting to already running instance of WebSphere."));
		}

		if (ILaunchManager.RUN_MODE.equals(mode)) {
			runLaunch(server, configuration, launch, monitor);
		}
		else if (ILaunchManager.DEBUG_MODE.equals(mode)) {
			debugLaunch(server, configuration, launch, monitor);
		}
		else {
			throw new CoreException(AWSCorePlugin.createErrorStatus("Profile mode is not supported."));
		}
	}

	@SuppressWarnings({
		"rawtypes", "unchecked", "deprecation"
	})
	protected void debugLaunch(
		IServer server, ILaunchConfiguration configuration, ILaunch launch, IProgressMonitor monitor)
		throws CoreException {

		if (monitor == null) {
			monitor = new NullProgressMonitor();
		}

		// setup the run launch so we get console monitor
		runLaunch(server, configuration, launch, monitor);

		String connectorId = getVMConnectorId(configuration);

		IVMConnector connector = null;

		if (connectorId == null) {
			connector = JavaRuntime.getDefaultVMConnector();
		}
		else {
			connector = JavaRuntime.getVMConnector(connectorId);
		}

		if (connector == null) {
			abort(
				"Debugging connector not specified.", null,
				IJavaLaunchConfigurationConstants.ERR_CONNECTOR_NOT_AVAILABLE);
		}

		Map connectMap = configuration.getAttribute(IJavaLaunchConfigurationConstants.ATTR_CONNECT_MAP, (Map) null);

		int connectTimeout = JavaRuntime.getPreferences().getInt(JavaRuntime.PREF_CONNECT_TIMEOUT);

		connectMap.put("timeout", "" + connectTimeout);

		// check for cancellation
		if (monitor.isCanceled()) {
			return;
		}

		// set the default source locator if required
		setDefaultSourceLocator(launch, configuration);

		if (!launch.isTerminated()) {
			// connect to remote VM
			connector.connect(connectMap, monitor, launch);
		}

		// check for cancellation
		if (monitor.isCanceled() || launch.isTerminated()) {
			IDebugTarget[] debugTargets = launch.getDebugTargets();
			for (int i = 0; i < debugTargets.length; i++) {
				IDebugTarget target = debugTargets[i];
				if (target.canDisconnect()) {
					target.disconnect();
				}
			}
			return;
		}

		monitor.done();
	}

	protected void runLaunch(
		IServer server, ILaunchConfiguration configuration, ILaunch launch, IProgressMonitor monitor)
		throws CoreException {

		// IWebsphereAdminService adminService =
		// WebsphereCore.getWebsphereAdminService((IBeanstalkServer) server.loadAdapter(
		// IBeanstalkServer.class, monitor));

		BeanstalkMonitorProcess process = new BeanstalkMonitorProcess(server, new Object(), launch);

		launch.addProcess(process);
	}

}
