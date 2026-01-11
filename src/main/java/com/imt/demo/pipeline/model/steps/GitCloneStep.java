package com.imt.demo.pipeline.model.steps;

import com.imt.demo.pipeline.model.PipelineContext;
import com.imt.demo.pipeline.model.StepResult;
import com.imt.demo.pipeline.model.StepStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
@Component
public class GitCloneStep extends AbstractPipelineStep {

    @Override
    public String getName() {
        return "Git Clone";
    }

    @Override
    public StepResult execute(PipelineContext context) throws Exception {
        Path workspacePath = Paths.get(context.getWorkspaceDir());
        if (!Files.exists(workspacePath)) {
            Files.createDirectories(workspacePath);
        }

        File targetDir = new File(context.getWorkspaceDir());
        if (targetDir.exists()) {
            deleteDirectory(targetDir);
        }

        Files.createDirectories(workspacePath);

        String[] command = {
            "git", "clone",
            "--branch", context.getGitBranch(),
            "--depth", "1",
            context.getGitRepoUrl(),
            "."
        };

        StepResult result = executeCommand(command, context.getWorkspaceDir());

        if (result.getStatus() == StepStatus.SUCCESS) {
            String[] getCommitHash = {"git", "rev-parse", "HEAD"};
            StepResult hashResult = executeCommand(getCommitHash, context.getWorkspaceDir());

            if (!hashResult.getLogs().isEmpty()) {
                String commitHash = hashResult.getLogs().get(hashResult.getLogs().size() - 1).trim();
                context.setCommitHash(commitHash);
                result.addLog("Commit hash: " + commitHash);
            }
        }

        return result;
    }

    @Override
    public void rollback(PipelineContext context) throws Exception {
        File workspaceDir = new File(context.getWorkspaceDir());
        if (workspaceDir.exists()) {
            deleteDirectory(workspaceDir);
            log.info("Workspace nettoye: {}", context.getWorkspaceDir());
        }
    }

    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }
}
