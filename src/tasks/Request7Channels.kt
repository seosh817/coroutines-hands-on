package tasks

import contributors.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

suspend fun loadContributorsChannels(
    service: GitHubService,
    req: RequestData,
    updateResults: suspend (List<User>, completed: Boolean) -> Unit
) {
    coroutineScope {
        val repos = service
            .getOrgRepos(req.org)
            .also { logRepos(req, it) }
            .body() ?: listOf()

        val allUsers = mutableListOf<User>()

        for((index, repo) in repos.withIndex()) {
            val users = service
                .getRepoContributors(req.org, repo.name)
                .also { logUsers(repo, it) }
                .bodyList()
            allUsers += users
            allUsers.aggregate()
            val isCompleted = index == repos.lastIndex
            updateResults(allUsers, isCompleted)
        }
        val channel = Channel<List<User>>()
        for (repo in repos) {
            launch {
                // delay는 필요 없으니 contributors 부르는 코드만 실행
                // 이 users 결과들을 모으기 위해서 channel을 사용
                // 서로다른 코루틴끼리 채널을 통해서 소통
                // 한곳에서 receive()해서 결과를 받아올 수 있도록 하겠음.
                // 개별 contributors들을 모으기 위해서 channel을 사용
                val users = service
                    .getRepoContributors(req.org, repo.name)
                    .also { logUsers(repo, it) }
                    .bodyList()

                // 모은다.
                channel.send(users)
            }
        }

        // 모은 후, repos 갯수만큼 채널을 받아와야함.
        // repos의 size는 알고 있으므로
        repeat(repos.size) { index ->
            // receive해서 각각의 users를 받아옴.
            val users = channel.receive()

            // 계속 받아와서 전체 수집한 allUsers를 만들 것임.
            allUsers += users
            val isCompleted = index == repos.lastIndex
            // aggregate()로 user 중복을 제거
            updateResults(allUsers.aggregate(), isCompleted)
            //순서는 상관없음 어짜피 빨리 보낸 애가 빨리 Send를 하고 Repos의 갯수만큼 receive()를 받으면 되기 때문.
        }
    }
}
