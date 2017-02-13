/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.dashboard.tree;

import com.intellij.execution.Executor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.dashboard.DashboardRunConfigurationNode;
import com.intellij.execution.dashboard.DashboardRunConfigurationStatus;
import com.intellij.execution.dashboard.RunDashboardContributor;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.content.Content;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Collections;

/**
 * @author konstantin.aleev
 */
class RunConfigurationNode  extends AbstractTreeNode<Pair<RunnerAndConfigurationSettings, RunContentDescriptor>>
  implements DashboardRunConfigurationNode {
  public RunConfigurationNode(Project project, @NotNull RunnerAndConfigurationSettings configurationSettings,
                                 @Nullable RunContentDescriptor descriptor) {
    super(project, Pair.create(configurationSettings, descriptor));
  }

  @Override
  @NotNull
  public RunnerAndConfigurationSettings getConfigurationSettings() {
    //noinspection ConstantConditions ???
    return getValue().getFirst();
  }

  @Nullable
  @Override
  public RunContentDescriptor getDescriptor() {
    //noinspection ConstantConditions ???
    return getValue().getSecond();
  }

  @Override
  protected void update(PresentationData presentation) {
    RunnerAndConfigurationSettings configurationSettings = getConfigurationSettings();
    boolean isStored = RunManager.getInstance(getProject()).getAllConfigurationsList().contains(configurationSettings.getConfiguration());
    presentation.addText(configurationSettings.getName(),
                         isStored ? SimpleTextAttributes.REGULAR_ATTRIBUTES : SimpleTextAttributes.GRAY_ATTRIBUTES);
    RunDashboardContributor contributor = RunDashboardContributor.getContributor(configurationSettings.getType());
    assert contributor != null;
    Icon icon = null;
    DashboardRunConfigurationStatus status = contributor.getStatus(this);
    if (DashboardRunConfigurationStatus.STARTED.equals(status)) {
      icon = getExecutorIcon();
    } else if (DashboardRunConfigurationStatus.FAILED.equals(status)) {
      icon = status.getIcon();
    }
    if (icon == null) {
      icon = RunManagerEx.getInstanceEx(getProject()).getConfigurationIcon(configurationSettings);
    }
    presentation.setIcon(isStored ? icon : IconLoader.getDisabledIcon(icon));

    contributor.updatePresentation(presentation, this);
  }

  @NotNull
  @Override
  public Collection<? extends AbstractTreeNode> getChildren() {
    return Collections.emptyList();
  }

  @Nullable
  private Icon getExecutorIcon() {
    Content content = getContent();
    if (content != null) {
      if (!RunContentManagerImpl.isTerminated(content)) {
        Executor executor = RunContentManagerImpl.getExecutorByContent(content);
        if (executor != null) {
          return executor.getIcon();
        }
      }
    }
    return null;
  }
}
