package com.kng7.sevenpro.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID

/**
 * Simple file-backed storage for profiles and subscriptions (Gson + JSON files).
 * Thread-safe; all mutations happen on a synchronized monitor.
 */
object ProfileStore {

    private val gson = Gson()
    private val lock = Any()
    private var profiles: MutableList<Profile> = mutableListOf()
    private var subs: MutableList<Subscription> = mutableListOf()
    private lateinit var profilesFile: File
    private lateinit var subsFile: File

    fun init(context: Context) {
        synchronized(lock) {
            profilesFile = File(context.filesDir, "profiles.json")
            subsFile = File(context.filesDir, "subscriptions.json")
            profiles = readJson(profilesFile) ?: mutableListOf()
            subs = readJson(subsFile) ?: mutableListOf()
        }
    }

    private inline fun <reified T> readJson(file: File): T? {
        if (!file.exists()) return null
        return try {
            gson.fromJson(file.readText(), object : TypeToken<T>() {}.type)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveLocked() {
        try {
            profilesFile.writeText(gson.toJson(profiles))
            subsFile.writeText(gson.toJson(subs))
        } catch (_: Exception) {
        }
    }

    // ---------- Profiles ----------

    fun allProfiles(): List<Profile> = synchronized(lock) { profiles.toList() }

    fun getProfile(id: String): Profile? = synchronized(lock) { profiles.firstOrNull { it.id == id }?.copy() }

    fun selectedProfile(selectedId: String): Profile? = getProfile(selectedId)

    fun addProfile(p: Profile) {
        synchronized(lock) {
            if (p.id.isBlank()) p.id = UUID.randomUUID().toString()
            profiles.add(p)
            saveLocked()
        }
    }

    fun updateProfile(p: Profile) {
        synchronized(lock) {
            val idx = profiles.indexOfFirst { it.id == p.id }
            if (idx >= 0) profiles[idx] = p else profiles.add(p)
            saveLocked()
        }
    }

    fun deleteProfile(id: String) {
        synchronized(lock) {
            profiles.removeAll { it.id == id }
            saveLocked()
        }
    }

    fun updatePing(id: String, pingMs: Int) {
        synchronized(lock) {
            val p = profiles.firstOrNull { it.id == id } ?: return
            p.pingMs = pingMs
            saveLocked()
        }
    }

    /** Returns true if a profile with the same signature already exists. */
    fun containsSignature(sig: String): Boolean =
        synchronized(lock) { profiles.any { it.signature() == sig } }

    // ---------- Subscriptions ----------

    fun allSubs(): List<Subscription> = synchronized(lock) { subs.toList() }

    fun addSub(s: Subscription) {
        synchronized(lock) {
            if (s.id.isBlank()) s.id = UUID.randomUUID().toString()
            subs.add(s)
            saveLocked()
        }
    }

    fun updateSub(s: Subscription) {
        synchronized(lock) {
            val idx = subs.indexOfFirst { it.id == s.id }
            if (idx >= 0) subs[idx] = s else subs.add(s)
            saveLocked()
        }
    }

    fun deleteSub(id: String) {
        synchronized(lock) {
            subs.removeAll { it.id == id }
            profiles.removeAll { it.subscriptionId == id }
            saveLocked()
        }
    }

    /**
     * Merge profiles fetched from a subscription:
     * existing (same signature) profiles are kept untouched, new ones are added,
     * removed ones (no longer in the fetch result) are deleted.
     */
    fun mergeSubscription(subId: String, fetched: List<Profile>): Int {
        synchronized(lock) {
            val existing = profiles.filter { it.subscriptionId == subId }
            val fetchedSigs = fetched.map { it.signature() }.toSet()
            // Remove stale
            profiles.removeAll { it.subscriptionId == subId && it.signature() !in fetchedSigs }
            var added = 0
            fetched.forEach { p ->
                if (profiles.none { it.subscriptionId == subId && it.signature() == p.signature() }) {
                    p.id = UUID.randomUUID().toString()
                    p.subscriptionId = subId
                    profiles.add(p)
                    added++
                }
            }
            // If an existing profile with same signature exists outside this subscription, skip duplicates
            saveLocked()
            return added
        }
    }
}
