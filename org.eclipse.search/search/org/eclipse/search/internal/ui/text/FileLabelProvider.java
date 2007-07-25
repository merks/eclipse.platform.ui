/*******************************************************************************
 * Copyright (c) 2000, 2007 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Juerg Billeter, juergbi@ethz.ch - 47136 Search view should show match objects
 *     Ulrich Etter, etteru@ethz.ch - 47136 Search view should show match objects
 *     Roman Fuchs, fuchsro@ethz.ch - 47136 Search view should show match objects
 *******************************************************************************/
package org.eclipse.search.internal.ui.text;

import com.ibm.icu.text.MessageFormat;

import org.eclipse.core.runtime.IPath;

import org.eclipse.core.resources.IResource;

import org.eclipse.swt.graphics.Image;

import org.eclipse.jface.viewers.ILabelProviderListener;
import org.eclipse.jface.viewers.LabelProvider;

import org.eclipse.ui.model.WorkbenchLabelProvider;

import org.eclipse.search.ui.text.AbstractTextSearchResult;
import org.eclipse.search.ui.text.AbstractTextSearchViewPage;

import org.eclipse.search.internal.ui.SearchMessages;

public class FileLabelProvider extends LabelProvider implements IRichLabelProvider {
		
	public static final int SHOW_LABEL= 1;
	public static final int SHOW_LABEL_PATH= 2;
	public static final int SHOW_PATH_LABEL= 3;
	
	private static final String fgSeparatorFormat= "{0} - {1}"; //$NON-NLS-1$
	
	private WorkbenchLabelProvider fLabelProvider;
	private AbstractTextSearchViewPage fPage;
		
	private int fOrder;

	public FileLabelProvider(AbstractTextSearchViewPage page, int orderFlag) {
		fLabelProvider= new WorkbenchLabelProvider();
		fOrder= orderFlag;
		fPage= page;
	}

	public void setOrder(int orderFlag) {
		fOrder= orderFlag;
	}
	
	public int getOrder() {
		return fOrder;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.LabelProvider#getText(java.lang.Object)
	 */
	public String getText(Object object) {
		return getRichTextLabel(object).getString();
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.search.internal.ui.text.IRichLabelProvider#getRichTextLabel(java.lang.Object)
	 */
	public ColoredString getRichTextLabel(Object element) {
		if (element instanceof FileMatch) {
			FileMatch match= (FileMatch) element;
			ColoredString str= new ColoredString(match.getLine());
			str.colorize(match.getOffsetWithinLine(), match.getLengthWithinLine(), ColoredViewersManager.HIGHLIGHT_STYLE);
			return str;
		}
		
		if (!(element instanceof IResource))
			return new ColoredString();

		IResource resource= (IResource) element;
		if (!resource.exists())
			new ColoredString(SearchMessages.FileLabelProvider_removed_resource_label); 
		
		if (fOrder == SHOW_LABEL)
			return getColoredLabelWithCounts(resource, new ColoredString(resource.getName()));
		
		IPath path= resource.getParent().getFullPath().makeRelative();
		if (fOrder == SHOW_LABEL_PATH) {
			ColoredString str= new ColoredString(resource.getName());
			String decorated= MessageFormat.format(fgSeparatorFormat, new String[] { str.getString(),  path.toString() });
			ColoredViewersManager.decorateColoredString(str, decorated, ColoredViewersManager.QUALIFIER_STYLE);
			return getColoredLabelWithCounts(resource, str);
		}

		ColoredString str= new ColoredString(MessageFormat.format(fgSeparatorFormat, new String[] { path.toString(), resource.getName() }));
		return getColoredLabelWithCounts(resource, str);
	}
	
	private ColoredString getColoredLabelWithCounts(Object element, ColoredString coloredName) {
		AbstractTextSearchResult result= fPage.getInput();
		if (result == null)
			return coloredName;
			
		int matchCount= result.getMatchCount(element);
		if (matchCount <= 1)
			return coloredName;
		
		String decorated= MessageFormat.format(SearchMessages.FileLabelProvider_count_format, new Object[] { coloredName.getString(), new Integer(matchCount) });
		ColoredViewersManager.decorateColoredString(coloredName, decorated, ColoredViewersManager.COUNTER_STYLE);
		return coloredName;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.LabelProvider#getImage(java.lang.Object)
	 */
	public Image getImage(Object element) {
		if (element instanceof FileMatch) {
			return getImage(((FileMatch) element).getElement()); // return image of corresponding file
		}
		if (!(element instanceof IResource))
			return null;

		IResource resource= (IResource)element;
		Image image= fLabelProvider.getImage(resource);
		return image;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.BaseLabelProvider#dispose()
	 */
	public void dispose() {
		super.dispose();
		fLabelProvider.dispose();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.BaseLabelProvider#isLabelProperty(java.lang.Object, java.lang.String)
	 */
	public boolean isLabelProperty(Object element, String property) {
		return fLabelProvider.isLabelProperty(element, property);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.BaseLabelProvider#removeListener(org.eclipse.jface.viewers.ILabelProviderListener)
	 */
	public void removeListener(ILabelProviderListener listener) {
		super.removeListener(listener);
		fLabelProvider.removeListener(listener);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jface.viewers.BaseLabelProvider#addListener(org.eclipse.jface.viewers.ILabelProviderListener)
	 */
	public void addListener(ILabelProviderListener listener) {
		super.addListener(listener);
		fLabelProvider.addListener(listener);
	}
}
