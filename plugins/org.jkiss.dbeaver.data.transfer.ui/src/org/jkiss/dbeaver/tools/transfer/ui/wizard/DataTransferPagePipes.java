/*
 * DBeaver - Universal Database Manager
 * Copyright (C) 2010-2021 DBeaver Corp and others
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jkiss.dbeaver.tools.transfer.ui.wizard;

import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.jkiss.dbeaver.model.DBPEvaluationContext;
import org.jkiss.dbeaver.model.DBPImage;
import org.jkiss.dbeaver.model.DBUtils;
import org.jkiss.dbeaver.model.DBValueFormatting;
import org.jkiss.dbeaver.model.navigator.DBNDatabaseNode;
import org.jkiss.dbeaver.model.navigator.DBNModel;
import org.jkiss.dbeaver.model.struct.DBSObject;
import org.jkiss.dbeaver.runtime.DBWorkbench;
import org.jkiss.dbeaver.tools.transfer.DataTransferSettings;
import org.jkiss.dbeaver.tools.transfer.internal.DTMessages;
import org.jkiss.dbeaver.tools.transfer.registry.DataTransferNodeDescriptor;
import org.jkiss.dbeaver.tools.transfer.registry.DataTransferProcessorDescriptor;
import org.jkiss.dbeaver.tools.transfer.registry.DataTransferRegistry;
import org.jkiss.dbeaver.ui.DBeaverIcons;
import org.jkiss.dbeaver.ui.UIUtils;
import org.jkiss.dbeaver.ui.controls.ListContentProvider;
import org.jkiss.dbeaver.ui.dialogs.ActiveWizardPage;
import org.jkiss.utils.CommonUtils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

class DataTransferPagePipes extends ActiveWizardPage<DataTransferWizard> {

    private boolean activated;
    private TableViewer nodesTable;
    private TableViewer inputsTable;

    private static class TransferTarget {
        DataTransferNodeDescriptor node;
        DataTransferProcessorDescriptor processor;

        private TransferTarget(DataTransferNodeDescriptor node, DataTransferProcessorDescriptor processor)
        {
            this.node = node;
            this.processor = processor;
        }
    }

    DataTransferPagePipes() {
        super(DTMessages.data_transfer_wizard_init_name);
    }

    @Override
    public void createControl(Composite parent) {
        initializeDialogUnits(parent);

        Composite composite = UIUtils.createComposite(parent, 1);

        SashForm sash = new SashForm(composite, SWT.HORIZONTAL);
        sash.setLayoutData(new GridData(GridData.FILL_BOTH));

        createNodesTable(sash);
        createInputsTable(sash);
        sash.setWeights(new int[]{60, 40});

        setControl(composite);
    }

    private void createNodesTable(Composite composite) {
        Composite panel = UIUtils.createComposite(composite, 1);

        //UIUtils.createControlLabel(panel, DTUIMessages.data_transfer_wizard_final_column_target);

        nodesTable = new TableViewer(panel, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);
        nodesTable.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));
        nodesTable.getTable().setLinesVisible(true);
        nodesTable.setContentProvider(new IStructuredContentProvider() {
            @Override
            public void dispose()
            {
            }

            @Override
            public void inputChanged(Viewer viewer, Object oldInput, Object newInput)
            {
            }

            @Override
            public Object[] getElements(Object inputElement)
            {
                if (inputElement instanceof Collection) {
                    return ((Collection<?>) inputElement).toArray();
                }
                return new Object[0];
            }
        });
        CellLabelProvider labelProvider = new CellLabelProvider() {
            @Override
            public void update(ViewerCell cell) {
                TransferTarget element = (TransferTarget) cell.getElement();
                if (cell.getColumnIndex() == 0) {
                    if (element.processor != null) {
                        cell.setImage(DBeaverIcons.getImage(element.processor.getIcon()));
                        cell.setText(element.processor.getName());
                    } else {
                        cell.setImage(DBeaverIcons.getImage(element.node.getIcon()));
                        cell.setText(element.node.getName());
                    }
                } else {
                    if (element.processor != null) {
                        cell.setText(element.processor.getDescription());
                    } else {
                        cell.setText(element.node.getDescription());
                    }
                }
            }
        };
        {
            TableViewerColumn columnName = new TableViewerColumn(nodesTable, SWT.LEFT);
            columnName.setLabelProvider(labelProvider);
            columnName.getColumn().setText(DTMessages.data_transfer_wizard_init_column_exported);

            TableViewerColumn columnDesc = new TableViewerColumn(nodesTable, SWT.LEFT);
            columnDesc.setLabelProvider(labelProvider);
            columnDesc.getColumn().setText(DTMessages.data_transfer_wizard_init_column_description);
        }

        nodesTable.getTable().addSelectionListener(new SelectionListener() {
            @Override
            public void widgetSelected(SelectionEvent e)
            {
                setSelectedSettings();
            }

            @Override
            public void widgetDefaultSelected(SelectionEvent e)
            {
                widgetSelected(e);
                if (isPageComplete()) {
                    getWizard().getContainer().showPage(getWizard().getNextPage(DataTransferPagePipes.this));
                }
            }
        });
    }

    private void setSelectedSettings() {
        final IStructuredSelection selection = (IStructuredSelection) nodesTable.getSelection();
        TransferTarget target;
        if (!selection.isEmpty()) {
            target = (TransferTarget) selection.getFirstElement();
        } else {
            target = null;
        }
        DataTransferSettings settings = getWizard().getSettings();
        if (target == null) {
            settings.selectConsumer(null, null, true);
        } else {
            if (settings.isConsumerOptional()) {
                settings.selectConsumer(target.node, target.processor, true);
            } else if (settings.isProducerOptional()) {
                settings.selectProducer(target.node, target.processor, true);
            } else {
                // no optional nodes
            }
        }
        updatePageCompletion();
    }

    private void createInputsTable(Composite composite) {
        Composite panel = UIUtils.createComposite(composite, 1);

        //UIUtils.createControlLabel(panel, DTUIMessages.data_transfer_wizard_final_group_objects);

        inputsTable = new TableViewer(panel, SWT.BORDER | SWT.SINGLE | SWT.FULL_SELECTION);
        inputsTable.getTable().setLayoutData(new GridData(GridData.FILL_BOTH));
        inputsTable.getTable().setLinesVisible(true);
        inputsTable.getTable().setHeaderVisible(true);
        inputsTable.setContentProvider(new ListContentProvider());
        UIUtils.createTableContextMenu(inputsTable.getTable(), null);
        DBNModel nModel = DBWorkbench.getPlatform().getNavigatorModel();
        CellLabelProvider labelProvider = new CellLabelProvider() {
            @Override
            public void update(ViewerCell cell) {
                DBSObject element = (DBSObject) cell.getElement();
                if (cell.getColumnIndex() == 0) {
                    DBNDatabaseNode objectNode = nModel.getNodeByObject(element);
                    DBPImage icon = objectNode != null ? objectNode.getNodeIconDefault() : DBValueFormatting.getObjectImage(element);
                    cell.setImage(DBeaverIcons.getImage(icon));
                    cell.setText(DBUtils.getObjectFullName(element, DBPEvaluationContext.UI));
                } else if (element.getDescription() != null) {
                    cell.setText(element.getDescription());
                }
            }
        };
        {
            TableViewerColumn columnName = new TableViewerColumn(inputsTable, SWT.LEFT);
            columnName.setLabelProvider(labelProvider);
            columnName.getColumn().setText(DTMessages.data_transfer_wizard_init_column_exported);

            TableViewerColumn columnDesc = new TableViewerColumn(inputsTable, SWT.LEFT);
            columnDesc.setLabelProvider(labelProvider);
            columnDesc.getColumn().setText(DTMessages.data_transfer_wizard_init_column_description);
        }
    }

    @Override
    public void activatePage() {
        if (activated) {
            // Second activation - we need to disable any selectors
            if (getWizard().getSettings().isPipeChangeRestricted() || getWizard().isTaskEditor()) {
                nodesTable.getTable().setEnabled(false);
            }
            return;
        }
        activated = true;

        UIUtils.asyncExec(this::loadNodeSettings);
    }

    private void loadNodeSettings() {
        try {
            getWizard().getRunnableContext().run(true, true, monitor -> {
                getWizard().getSettings().loadNodeSettings(monitor);
            });
        } catch (InvocationTargetException e) {
            DBWorkbench.getPlatformUI().showError("Error loading settings", "Error loading data transfer settings", e.getTargetException());
        } catch (InterruptedException e) {
            // ignore
        }

        if (getWizard().getSettings().isConsumerOptional()) {
            setTitle(DTMessages.data_transfer_wizard_init_title);
            setDescription(DTMessages.data_transfer_wizard_init_description);

            loadConsumers();
        } else {
            setTitle(DTMessages.data_transfer_wizard_producers_title);
            setDescription(DTMessages.data_transfer_wizard_producers_description);

            loadProducers();
        }

        DataTransferNodeDescriptor consumer = getWizard().getSettings().getConsumer();
        DataTransferNodeDescriptor producer = getWizard().getSettings().getProducer();
        DataTransferProcessorDescriptor processor = getWizard().getSettings().getProcessor();
        if (consumer != null || producer != null) {
            Collection<TransferTarget> targets = (Collection<TransferTarget>) nodesTable.getInput();
            for (TransferTarget target : targets) {
                if ((target.node == consumer || target.node == producer) && target.processor == processor) {
                    UIUtils.asyncExec(() -> {
                        nodesTable.setSelection(new StructuredSelection(target));
                        setSelectedSettings();
                    });
                    break;
                }
            }
        }

        inputsTable.setInput(getWizard().getSettings().getSourceObjects());

        UIUtils.maxTableColumnsWidth(inputsTable.getTable());
        UIUtils.maxTableColumnsWidth(nodesTable.getTable());

        updatePageCompletion();
    }

    private void loadConsumers()
    {
        DataTransferSettings settings = getWizard().getSettings();
        Collection<DBSObject> objects = settings.getSourceObjects();

        List<TransferTarget> transferTargets = new ArrayList<>();
        for (DataTransferNodeDescriptor consumer : DataTransferRegistry.getInstance().getAvailableConsumers(objects)) {
            Collection<DataTransferProcessorDescriptor> processors = consumer.getAvailableProcessors(objects);
            if (CommonUtils.isEmpty(processors)) {
                transferTargets.add(new TransferTarget(consumer, null));
            } else {
                for (DataTransferProcessorDescriptor processor : processors) {
                    transferTargets.add(new TransferTarget(consumer, processor));
                }
            }
        }
        nodesTable.setInput(transferTargets);
    }

    private void loadProducers()
    {
        DataTransferSettings settings = getWizard().getSettings();
        Collection<DBSObject> objects = settings.getSourceObjects();

        List<TransferTarget> transferTargets = new ArrayList<>();
        for (DataTransferNodeDescriptor producer : DataTransferRegistry.getInstance().getAvailableProducers(objects)) {
            Collection<DataTransferProcessorDescriptor> processors = producer.getAvailableProcessors(objects);
            if (CommonUtils.isEmpty(processors)) {
                transferTargets.add(new TransferTarget(producer, null));
            } else {
                for (DataTransferProcessorDescriptor processor : processors) {
                    transferTargets.add(new TransferTarget(producer, processor));
                }
            }
        }
        nodesTable.setInput(transferTargets);
    }

    @Override
    protected boolean determinePageCompletion() {
        DataTransferSettings settings = getWizard().getSettings();
        if (settings.getConsumer() == null || settings.getProducer() == null) {
            return false;
        }
//        if (settings.isProducerOptional()) {
//            settings.setProcessorProperties();
//        }

        return true;
    }

}