package bb.testask.githubusersearch.datamodel

import bb.testask.githubusersearch.dao.*
import bb.testask.githubusersearch.model.ProfileResponse
import bb.testask.githubusersearch.model.UserEntry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provide User data from local DB
 */
@Singleton
class UserLocalModel @Inject constructor(private val daoSession: DaoSession) {

    private val userDao: UserDao = daoSession.userDao
    private val queryDao: QueryDao = daoSession.queryDao
    private val query2UserDao: Query2UserDao = daoSession.query2UserDao

    /**
     * Save search query from UI and users from API response
     */
    fun saveUsers(query: String, users: List<UserEntry>) {
        if (users.isEmpty()) return
        daoSession.runInTx {
            val dbQuery = saveQuery(query)
            val userIds = saveUser(users)
            updateQuery2Users(dbQuery, userIds)
        }
    }

    /**
     * Get local saved users
     */
    fun getUsers(query: String): List<User> {
        val dbQuery = findQueryByPK(query) ?: return emptyList()
        queryDao.updateInTx(dbQuery.apply { execDate = System.currentTimeMillis() })
        val builder = userDao.queryBuilder()
        builder.join(Query2User::class.java, Query2UserDao.Properties.UserId)
                .where(Query2UserDao.Properties.QueryId.eq(dbQuery.id))
        return builder.list()
    }

    /**
     * Try to find last success query to restore UI state on launch
     */
    fun getLastQuery(): String = queryDao.queryBuilder()
            .orderDesc(QueryDao.Properties.ExecDate)
            .list().firstOrNull()?.query ?: ""

    /**
     * Save user details info
     */
    fun saveProfile(profile: ProfileResponse) {
        val user = findUserByPK(profile.id) ?: return
        daoSession.runInTx {
            user.apply {
                name = profile.name
                bio = profile.bio
            }
            userDao.update(user)
        }
    }

    fun getProfile(serverId: Int): User = findUserByPK(serverId) ?: User()

    /**
     * Delete all local saved users and queries
     */
    fun clear() {
        daoSession.runInTx {
            userDao.deleteAll()
            queryDao.deleteAll()
            query2UserDao.deleteAll()
        }
    }

    private fun saveQuery(query: String): Query {
        var dbQuery = findQueryByPK(query)
        if (dbQuery == null) {
            dbQuery = Query().apply { this.query = query; execDate = System.currentTimeMillis() }
            queryDao.insert(dbQuery)
        } else {
            dbQuery.execDate = System.currentTimeMillis()
            queryDao.update(dbQuery)
        }
        return dbQuery
    }

    private fun saveUser(users: List<UserEntry>): List<Long> {
        val res = ArrayList<Long>()
        users.forEach {
            val dbUser = findUserByPK(it.id) ?: User().apply { serverId = it.id }
            dbUser.apply {
                login = it.login
                avatarUrl = it.avatarUrl
            }
            res.add(userDao.insertOrReplace(dbUser))
        }
        return res
    }

    private fun updateQuery2Users(query: Query, userIds: List<Long>) {
        query2UserDao.queryBuilder()
                .where(Query2UserDao.Properties.QueryId.eq(query.id))
                .buildDelete()
                .executeDeleteWithoutDetachingEntities()
        userIds.forEach {
            val dbItem = Query2User().apply { queryId = query.id; userId = it }
            query2UserDao.insert(dbItem)
        }
        query.resetUsers()
    }

    private fun findQueryByPK(query: String): Query? = queryDao.queryBuilder()
            .where(QueryDao.Properties.Query.eq(query))
            .limit(1)
            .unique()

    private fun findUserByPK(id: Int): User? = userDao.queryBuilder()
            .where(UserDao.Properties.ServerId.eq(id))
            .limit(1)
            .unique()

}