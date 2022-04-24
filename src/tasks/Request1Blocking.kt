package tasks

import contributors.*
import retrofit2.Response

fun loadContributorsBlocking(service: GitHubService, req: RequestData) : List<User> {
    val repos = service
        .getOrgReposCall(req.org)
        .execute() // Executes request and blocks the current thread // Call의 메소드 현재 스레드를 멈추게함.
        .also { logRepos(req, it) }
        .body() ?: listOf()

    return repos.flatMap { repo ->
        service
            .getRepoContributorsCall(req.org, repo.name)
            .execute() // Executes request and blocks the current thread //
            .also { logUsers(repo, it) }
            .bodyList()
    }.aggregate()
}

fun <T> Response<List<T>>.bodyList(): List<T> {
    return body() ?: listOf()
}