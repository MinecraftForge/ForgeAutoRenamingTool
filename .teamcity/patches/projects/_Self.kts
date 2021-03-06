package patches.projects

import jetbrains.buildServer.configs.kotlin.v2019_2.*
import jetbrains.buildServer.configs.kotlin.v2019_2.Project
import jetbrains.buildServer.configs.kotlin.v2019_2.projectFeatures.GitHubIssueTracker
import jetbrains.buildServer.configs.kotlin.v2019_2.projectFeatures.githubIssues
import jetbrains.buildServer.configs.kotlin.v2019_2.ui.*

/*
This patch script was generated by TeamCity on settings change in UI.
To apply the patch, change the root project
accordingly, and delete the patch script.
*/
changeProject(DslContext.projectId) {
    features {
        val feature1 = find<GitHubIssueTracker> {
            githubIssues {
                id = "ForgeAutoRenamingTool__IssueTracker"
                displayName = "MinecraftForge/ForgeAutoRenamingTool"
                repositoryURL = "https://github.com/MinecraftForge/ForgeAutoRenamingTool"
            }
        }
        feature1.apply {
        }
    }
}
