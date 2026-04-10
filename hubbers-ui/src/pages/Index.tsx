import { ResizableHandle, ResizablePanel, ResizablePanelGroup } from '@/components/ui/resizable';
import { WorkspaceHeader } from '@/components/workspace/WorkspaceHeader';
import { SidebarPanel } from '@/components/workspace/SidebarPanel';
import { EditorPanel } from '@/components/workspace/EditorPanel';
import { RunnerPanel } from '@/components/workspace/RunnerPanel';
import { FormModal } from '@/components/workspace/FormModal';
import { useWorkspace } from '@/hooks/useWorkspace';

export default function Index() {
  const ws = useWorkspace();

  return (
    <div className="h-screen flex flex-col overflow-hidden">
      <WorkspaceHeader
        apiOnline={ws.apiOnline}
        sidebarOpen={ws.sidebarOpen}
        onToggleSidebar={() => ws.setSidebarOpen(!ws.sidebarOpen)}
      />

      <ResizablePanelGroup direction="horizontal" className="flex-1 min-h-0">
        {ws.sidebarOpen && (
          <>
            <ResizablePanel defaultSize={25} minSize={18} maxSize={35}>
              <SidebarPanel
                repo={ws.repo}
                selected={ws.selected}
                loading={ws.loading.repo}
                onSelect={ws.selectArtifact}
                onRefresh={ws.loadRepo}
                onCreate={ws.createArtifact}
              />
            </ResizablePanel>
            <ResizableHandle withHandle />
          </>
        )}

        <ResizablePanel defaultSize={ws.sidebarOpen ? 45 : 60} minSize={30}>
          <EditorPanel
            selected={ws.selected}
            manifest={ws.manifest}
            onManifestChange={ws.setManifest}
            editorTab={ws.editorTab}
            onEditorTabChange={ws.setEditorTab}
            loading={ws.loading}
            onSave={ws.saveManifest}
            onValidate={ws.validateManifest}
            pipelineSteps={ws.pipelineSteps}
            onPipelineStepsChange={ws.setPipelineSteps}
            onSyncSteps={ws.syncStepsToYaml}
            executions={ws.executions}
            onLoadExecutions={ws.loadExecutions}
            selectedExecution={ws.selectedExecution}
            executionDetail={ws.executionDetail}
            execDetailTab={ws.execDetailTab}
            onExecDetailTabChange={ws.setExecDetailTab}
            onSelectExecution={ws.loadExecutionDetail}
            onBackToList={() => ws.setSelectedExecution(null)}
          />
        </ResizablePanel>

        <ResizableHandle withHandle />

        <ResizablePanel defaultSize={30} minSize={20}>
          <RunnerPanel
            runInput={ws.runInput}
            onRunInputChange={ws.setRunInput}
            runOutput={ws.runOutput}
            running={ws.loading.run}
            disabled={!ws.selected}
            onRun={ws.runArtifact}
          />
        </ResizablePanel>
      </ResizablePanelGroup>

      <FormModal
        open={ws.formModal.open}
        form={ws.formModal.form}
        data={ws.formModal.data}
        onDataChange={data => ws.setFormModal(prev => ({ ...prev, data }))}
        onSubmit={ws.submitFormModal}
        onCancel={() => ws.setFormModal({ open: false, data: {} })}
      />
    </div>
  );
}
