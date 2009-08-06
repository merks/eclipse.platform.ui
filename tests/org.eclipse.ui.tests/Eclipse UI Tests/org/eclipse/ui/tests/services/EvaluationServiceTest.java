/*******************************************************************************
 * Copyright (c) 2007, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.ui.tests.services;

import java.util.ArrayList;

import org.eclipse.core.expressions.EvaluationResult;
import org.eclipse.core.expressions.Expression;
import org.eclipse.core.expressions.ExpressionConverter;
import org.eclipse.core.expressions.ExpressionInfo;
import org.eclipse.core.expressions.IEvaluationContext;
import org.eclipse.core.internal.expressions.TestExpression;
import org.eclipse.core.internal.expressions.WithExpression;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jface.text.TextSelection;
import org.eclipse.jface.util.IPropertyChangeListener;
import org.eclipse.jface.util.PropertyChangeEvent;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreePath;
import org.eclipse.jface.viewers.TreeSelection;
import org.eclipse.ui.ISources;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchPart;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.contexts.IContextActivation;
import org.eclipse.ui.contexts.IContextService;
import org.eclipse.ui.internal.WorkbenchWindow;
import org.eclipse.ui.internal.services.SlaveEvaluationService;
import org.eclipse.ui.services.IEvaluationReference;
import org.eclipse.ui.services.IEvaluationService;
import org.eclipse.ui.services.ISourceProviderService;
import org.eclipse.ui.tests.SelectionProviderView;
import org.eclipse.ui.tests.commands.ActiveContextExpression;
import org.eclipse.ui.tests.harness.util.UITestCase;

/**
 * @since 3.3
 * 
 */
public class EvaluationServiceTest extends UITestCase {
	private static final String CONTEXT_ID1 = "org.eclipse.ui.command.contexts.evaluationService1";

	/**
	 * @param testName
	 */
	public EvaluationServiceTest(String testName) {
		super(testName);
	}

	private static class MyEval implements IPropertyChangeListener {
		public int count = 0;
		public boolean currentValue;

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.jface.util.IPropertyChangeListener#propertyChange(org.eclipse.jface.util.PropertyChangeEvent)
		 */
		public void propertyChange(PropertyChangeEvent event) {
			count++;
			if (event.getProperty() == IEvaluationService.RESULT
					&& event.getNewValue() instanceof Boolean) {
				currentValue = ((Boolean) event.getNewValue()).booleanValue();
			}
		}
	}

	public void testBasicService() throws Exception {
		IWorkbenchWindow window = openTestWindow();
		IEvaluationService service = (IEvaluationService) window
				.getService(IEvaluationService.class);
		assertNotNull(service);

		MyEval listener = new MyEval();
		IContextActivation context1 = null;
		IEvaluationReference evalRef = null;
		IContextService contextService = null;
		try {
			evalRef = service.addEvaluationListener(
					new ActiveContextExpression(CONTEXT_ID1,
							new String[] { ISources.ACTIVE_CONTEXT_NAME }),
					listener, IEvaluationService.RESULT);
			assertEquals(1, listener.count);
			assertFalse(listener.currentValue);

			contextService = (IContextService) window
					.getService(IContextService.class);
			context1 = contextService.activateContext(CONTEXT_ID1);
			assertEquals(2, listener.count);
			assertTrue(listener.currentValue);

			contextService.deactivateContext(context1);
			context1 = null;
			assertEquals(3, listener.count);
			assertFalse(listener.currentValue);

			service.removeEvaluationListener(evalRef);
			evalRef = null;
			assertEquals(4, listener.count);

			context1 = contextService.activateContext(CONTEXT_ID1);
			assertEquals(4, listener.count);
			assertFalse(listener.currentValue);
			contextService.deactivateContext(context1);
			context1 = null;
			assertEquals(4, listener.count);
			assertFalse(listener.currentValue);
		} finally {
			if (context1 != null) {
				contextService.deactivateContext(context1);
			}
			if (evalRef != null) {
				service.removeEvaluationListener(evalRef);
			}
		}
	}

	public void testTwoEvaluations() throws Exception {
		IWorkbenchWindow window = openTestWindow();
		IEvaluationService service = (IEvaluationService) window
				.getService(IEvaluationService.class);
		assertNotNull(service);

		MyEval listener1 = new MyEval();
		MyEval listener2 = new MyEval();
		IContextActivation context1 = null;
		IEvaluationReference evalRef1 = null;
		IEvaluationReference evalRef2 = null;
		IContextService contextService = null;
		try {
			evalRef1 = service.addEvaluationListener(
					new ActiveContextExpression(CONTEXT_ID1,
							new String[] { ISources.ACTIVE_CONTEXT_NAME }),
					listener1, IEvaluationService.RESULT);
			assertEquals(1, listener1.count);
			assertFalse(listener1.currentValue);

			evalRef2 = service.addEvaluationListener(
					new ActiveContextExpression(CONTEXT_ID1,
							new String[] { ISources.ACTIVE_CONTEXT_NAME }),
					listener2, IEvaluationService.RESULT);
			assertEquals(1, listener2.count);
			assertFalse(listener2.currentValue);
			evalRef2.setResult(true);

			contextService = (IContextService) window
					.getService(IContextService.class);
			context1 = contextService.activateContext(CONTEXT_ID1);
			assertEquals(2, listener1.count);
			assertTrue(listener1.currentValue);
			// we already set this guy to true, he should skip
			assertEquals(1, listener2.count);
			assertFalse(listener2.currentValue);

			evalRef1.setResult(false);
			contextService.deactivateContext(context1);
			context1 = null;
			assertEquals(2, listener2.count);
			assertFalse(listener2.currentValue);

			// we already set this guy to false, so he should be the old
			// values
			assertEquals(2, listener1.count);
			assertTrue(listener1.currentValue);

		} finally {
			if (context1 != null) {
				contextService.deactivateContext(context1);
			}
			if (evalRef1 != null) {
				service.removeEvaluationListener(evalRef1);
			}
			if (evalRef2 != null) {
				service.removeEvaluationListener(evalRef2);
			}
		}
	}

	public void testRestriction() {
		boolean temporarilyDisabled = true;
		if (temporarilyDisabled) return;
		
		IWorkbenchWindow window = openTestWindow();
		IEvaluationService evaluationService = (IEvaluationService) window
				.getService(IEvaluationService.class);
		assertNotNull(evaluationService);
		IContextService contextService = (IContextService) window
				.getService(IContextService.class);
		assertNotNull(contextService);

		Expression expression = new ActiveContextExpression(CONTEXT_ID1,
				new String[] { ISources.ACTIVE_CONTEXT_NAME });

		final boolean[] propertyChanged = new boolean[1];
		final boolean[] propertyShouldChange = new boolean[1];

		IPropertyChangeListener propertyChangeListener = new IPropertyChangeListener() {

			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty().equals("foo"))
					propertyChanged[0] = true;

			}
		};
		IEvaluationReference ref = evaluationService.addEvaluationListener(
				expression, propertyChangeListener, "foo");
		((WorkbenchWindow)window).getMenuRestrictions().add(ref);

		IPropertyChangeListener propertyShouldChangeListener = new IPropertyChangeListener() {

			public void propertyChange(PropertyChangeEvent event) {
				if (event.getProperty().equals("foo"))
					propertyShouldChange[0] = true;

			}
		};
		evaluationService.addEvaluationListener(expression,
				propertyShouldChangeListener, "foo");

		propertyChanged[0] = false;
		propertyShouldChange[0] = false;

		assertFalse(contextService.getActiveContextIds().contains(CONTEXT_ID1));
		IContextActivation activation = contextService
				.activateContext(CONTEXT_ID1);

		assertTrue(propertyChanged[0]);
		assertTrue(propertyShouldChange[0]);
		propertyChanged[0] = false;
		propertyShouldChange[0] = false;

		contextService.deactivateContext(activation);
		assertTrue(propertyChanged[0]);
		assertTrue(propertyShouldChange[0]);
		assertFalse(contextService.getActiveContextIds().contains(CONTEXT_ID1));
		activation = contextService.activateContext(CONTEXT_ID1);
		propertyChanged[0] = false;
		propertyShouldChange[0] = false;
		assertTrue(contextService.getActiveContextIds().contains(CONTEXT_ID1));

		// open second window
		IWorkbenchWindow window2 = openTestWindow();
		assertFalse(propertyChanged[0]);
		assertTrue(propertyShouldChange[0]);
		assertFalse(contextService.getActiveContextIds().contains(CONTEXT_ID1));
		propertyChanged[0] = false;
		propertyShouldChange[0] = false;

		window2.close();
		processEvents();

		assertTrue(contextService.getActiveContextIds().contains(CONTEXT_ID1));
		assertFalse(propertyChanged[0]);
		assertTrue(propertyShouldChange[0]);
	}

	public void testScopedService() throws Exception {
		IWorkbenchWindow window = openTestWindow();
		IEvaluationService service = (IEvaluationService) window
				.getService(IEvaluationService.class);
		assertNotNull(service);
		assertTrue(service instanceof SlaveEvaluationService);

		MyEval listener = new MyEval();
		IContextActivation context1 = null;
		IContextService contextService = null;
		try {
			service.addEvaluationListener(
					new ActiveContextExpression(CONTEXT_ID1,
							new String[] { ISources.ACTIVE_CONTEXT_NAME }),
					listener, IEvaluationService.RESULT);
			assertEquals(1, listener.count);
			assertFalse(listener.currentValue);

			contextService = (IContextService) window.getWorkbench()
					.getService(IContextService.class);
			context1 = contextService.activateContext(CONTEXT_ID1);
			assertEquals(2, listener.count);
			assertTrue(listener.currentValue);

			window.close();
			processEvents();
			assertEquals(3, listener.count);
			assertTrue(listener.currentValue);

			contextService.deactivateContext(context1);
			context1 = null;
			assertEquals(3, listener.count);
			assertTrue(listener.currentValue);
		} finally {
			if (context1 != null) {
				contextService.deactivateContext(context1);
			}
		}
	}

	private static class UserExpression extends Expression {
		public String lookFor;

		public UserExpression(String lookFor) {
			this.lookFor = lookFor;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see org.eclipse.core.expressions.Expression#collectExpressionInfo(org.eclipse.core.expressions.ExpressionInfo)
		 */
		public void collectExpressionInfo(ExpressionInfo info) {
			info.addVariableNameAccess("username");
		}

		public EvaluationResult evaluate(IEvaluationContext context)
				throws CoreException {
			String variable = (String) context.getVariable("username");
			return lookFor.equals(variable) ? EvaluationResult.TRUE
					: EvaluationResult.FALSE;
		}
	}

	public void testSourceProvider() throws Exception {
		IWorkbenchWindow window = openTestWindow();
		IEvaluationService service = (IEvaluationService) window
				.getService(IEvaluationService.class);
		assertNotNull(service);

		MyEval listener = new MyEval();
		UserExpression expression = new UserExpression("Paul");
		IEvaluationReference ref = service.addEvaluationListener(expression,
				listener, IEvaluationService.RESULT);
		assertEquals(ISources.ACTIVE_CONTEXT << 1, ref.getSourcePriority());
		assertFalse(listener.currentValue);
		assertEquals(1, listener.count);

		ISourceProviderService sps = (ISourceProviderService) window
				.getService(ISourceProviderService.class);
		ActiveUserSourceProvider userProvider = (ActiveUserSourceProvider) sps
				.getSourceProvider("username");

		userProvider.setUsername("John");
		assertFalse(listener.currentValue);
		assertEquals(1, listener.count);

		userProvider.setUsername("Paul");
		assertTrue(listener.currentValue);
		assertEquals(2, listener.count);

		userProvider.setUsername("guest");
		assertFalse(listener.currentValue);
		assertEquals(3, listener.count);
	}

	public void testPropertyChange() throws Exception {
		IWorkbenchWindow window = openTestWindow();
		IEvaluationService service = (IEvaluationService) window
				.getService(IEvaluationService.class);
		assertNotNull(service);
		MyEval listener = new MyEval();
		IExtensionRegistry registry = Platform.getExtensionRegistry();
		IConfigurationElement element = null;
		IConfigurationElement[] elements = registry
				.getConfigurationElementsFor("org.eclipse.core.expressions.definitions");
		for (int i = 0; i < elements.length && element == null; i++) {
			if (elements[i].getAttribute("id").equals(
					"org.eclipse.ui.tests.defWithPropertyTester")) {
				element = elements[i];
			}
		}

		assertNotNull(element);
		Expression expr = ExpressionConverter.getDefault().perform(element.getChildren()[0]);
		service.addEvaluationListener(expr,
				listener, IEvaluationService.RESULT);
		assertFalse(listener.currentValue);
		assertEquals(1, listener.count);
		
		StaticVarPropertyTester.result = true;
		assertFalse(listener.currentValue);
		assertEquals(1, listener.count);
		
		service.requestEvaluation("org.eclipse.ui.tests.class.method");
		assertTrue(listener.currentValue);
		assertEquals(2, listener.count);

		service.requestEvaluation("org.eclipse.ui.tests.class.method");
		assertTrue(listener.currentValue);
		assertEquals(2, listener.count);
	}
	
	public void testPlatformProperty() throws Exception {
		IEvaluationService evaluationService = (IEvaluationService) PlatformUI
				.getWorkbench().getService(IEvaluationService.class);
		TestExpression test = new TestExpression("org.eclipse.core.runtime",
				"bundleState",
				new Object[] { "org.eclipse.core.expressions" }, "ACTIVE", false);
		WithExpression exp = new WithExpression("org.eclipse.core.runtime.Platform");
		exp.add(test);
		EvaluationResult result= exp.evaluate(evaluationService.getCurrentState());
		assertEquals(EvaluationResult.TRUE, result);
	}
	
	public void XtestSystemProperty() throws Exception {
		// this is not added, as the ability to test system properties with
		// no '.' seems unhelpful
		System.setProperty("isHere", "true");
		IEvaluationService evaluationService = (IEvaluationService) PlatformUI
				.getWorkbench().getService(IEvaluationService.class);
		TestExpression test = new TestExpression("org.eclipse.core.runtime",
				"isHere",
				new Object[] { "true" }, null, false);
		WithExpression exp = new WithExpression(
				"java.lang.System" );
		exp.add(test);
		EvaluationResult result = exp.evaluate(evaluationService
				.getCurrentState());
		assertEquals(EvaluationResult.TRUE, result);

	}
	
	static class ActivePartIdExpression extends Expression {
		private String partId;

		public ActivePartIdExpression(String id) {
			partId = id;
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.core.expressions.Expression#collectExpressionInfo(org
		 * .eclipse.core.expressions.ExpressionInfo)
		 */
		public void collectExpressionInfo(ExpressionInfo info) {
			info.addVariableNameAccess(ISources.ACTIVE_PART_ID_NAME);
			info.addVariableNameAccess(ISources.ACTIVE_CURRENT_SELECTION_NAME);
		}

		/*
		 * (non-Javadoc)
		 * 
		 * @see
		 * org.eclipse.core.expressions.Expression#evaluate(org.eclipse.core
		 * .expressions.IEvaluationContext)
		 */
		public EvaluationResult evaluate(IEvaluationContext context){
			Object v = context.getVariable(ISources.ACTIVE_PART_ID_NAME);
			return EvaluationResult.valueOf(partId.equals(v));
		}
	}

	static class PartSelection {
		public ISelection selection;
		public IWorkbenchPart part;

		public PartSelection(ISelection sel, IWorkbenchPart p) {
			selection = sel;
			part = p;
		}
	}

	public void testWorkbenchProvider() throws Exception {
		
		IWorkbenchWindow window = openTestWindow();
		final IEvaluationService service = (IEvaluationService) window
				.getWorkbench().getService(IEvaluationService.class);
		assertNotNull(service);

		// some setup
		IWorkbenchPage page = window.getActivePage();
		SelectionProviderView view1 = (SelectionProviderView) page
				.showView(org.eclipse.ui.tests.SelectionProviderView.ID);
		view1.setSelection(StructuredSelection.EMPTY);
		SelectionProviderView view2 = (SelectionProviderView) page
				.showView(org.eclipse.ui.tests.SelectionProviderView.ID_2);
		TextSelection mySelection = new TextSelection(0, 5);
		view2.setSelection(mySelection);

		processEvents();

		final ArrayList selection = new ArrayList();
		IPropertyChangeListener listener = new IPropertyChangeListener() {
			public void propertyChange(PropertyChangeEvent event) {
				IEvaluationContext state = service.getCurrentState();
				try {
					ISelection sel = null;
					IWorkbenchPart part = null;
					Object o = state
							.getVariable(ISources.ACTIVE_CURRENT_SELECTION_NAME);
					if (o instanceof ISelection) {
						sel = (ISelection) o;
					}
					o = state.getVariable(ISources.ACTIVE_PART_NAME);
					if (o instanceof IWorkbenchPart) {
						part = (IWorkbenchPart) o;
					}
					selection.add(new PartSelection(sel, part));
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		};

		IEvaluationReference ref = service.addEvaluationListener(
				new ActivePartIdExpression(
						org.eclipse.ui.tests.SelectionProviderView.ID),
				listener, "PROP");
		int callIdx = 0;
		try {
			
			// initially ID_2 is showed
			assertSelection(selection, callIdx, TextSelection.class, SelectionProviderView.ID_2);
			
			page.activate(view1);
			processEvents();
			callIdx++;

			assertSelection(selection, callIdx, StructuredSelection.class, SelectionProviderView.ID);

			page.activate(view2);
			processEvents();
			callIdx++;

			assertSelection(selection, callIdx, TextSelection.class, SelectionProviderView.ID_2);
			assertEquals(window.getActivePage().getActivePart().getSite().getId(),
					service.getCurrentState().getVariable(ISources.ACTIVE_PART_ID_NAME));

			IWorkbenchWindow window2 = openTestWindow();
			IWorkbenchPage page2 = window2.getActivePage();
			processEvents();

			// no change
			assertEquals(callIdx + 1, selection.size());
			
			SelectionProviderView view3 = (SelectionProviderView) page2
					.showView(org.eclipse.ui.tests.SelectionProviderView.ID);
			processEvents();
			// id1 activated with default selection StructuredSelection.EMPTY
			callIdx++;

			assertSelection(selection, callIdx, StructuredSelection.class, SelectionProviderView.ID);
			assertEquals(window2.getActivePage().getActivePart().getSite().getId(),
					service.getCurrentState().getVariable(ISources.ACTIVE_PART_ID_NAME));
			
			view3.setSelection(new TreeSelection(new TreePath(new Object[] {"nothing"})));
			processEvents();
			// selection changes, but view id remains the same - so no callIdx increments
			assertEquals(callIdx + 1, selection.size());
			
			window.getShell().forceActive();
			processEvents();
			// the shell activate should have forced another change
			callIdx++;
//			assertEquals(window.getActivePage().getActivePart().getSite().getId(),
//					service.getCurrentState().getVariable(ISources.ACTIVE_PART_ID_NAME));

//			assertSelection(selection, callIdx, TextSelection.class, SelectionProviderView.ID_2);

		} finally {
			service.removeEvaluationListener(ref);
		}
	}

	private void assertSelection(final ArrayList selection, int callIdx, Class clazz, String viewId) {
		assertEquals(callIdx + 1, selection.size());
		assertEquals(clazz, getSelection(selection, callIdx)
				.getClass());
		assertEquals(viewId,
				getPart(selection, callIdx).getSite().getId());
	}

	private ISelection getSelection(final ArrayList selection, int idx) {
		return ((PartSelection) selection.get(idx)).selection;
	}

	private IWorkbenchPart getPart(final ArrayList selection, int idx) {
		return ((PartSelection) selection.get(idx)).part;
	}
}
