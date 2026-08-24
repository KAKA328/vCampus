# GitHub 初始化

本地首个提交完成后，组长在 GitHub 创建一个空仓库（不要自动生成 README），然后执行：

```bash
git remote add origin https://github.com/<组织或账号>/vCampus.git
git push -u origin main
```

如果使用 GitHub CLI：

```bash
gh auth login
gh repo create <组织或账号>/vCampus --private --source=. --remote=origin --push
```

推荐在 GitHub 设置：

- 保护 `main` 分支，要求 Pull Request 和 CI 通过；
- 每个组员使用个人分支，不共享账号；
- 开启 Issues 和 Pull Requests；
- 在仓库首页固定 README、设计基线和验收清单。
