package com.floatypet.data

import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.floatypet.asset.store.PetStore
import com.floatypet.core.model.Pet
import com.floatypet.core.model.PetAction
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "pet_prefs")
private val KEY_CURRENT_PET = stringPreferencesKey("current_pet_id")
private val KEY_PET_IDS = stringPreferencesKey("pet_ids")

@Singleton
class PetRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PetStore,
) : PetRepository {

    override val currentPet: Flow<Pet?> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_CURRENT_PET]?.takeIf { it.isNotBlank() }?.let { store.readPet(it) }
        }

    override val pets: Flow<List<Pet>> =
        context.dataStore.data.map { prefs ->
            // Ordered list from DataStore; filesystem scan recovers pets not yet in DataStore
            // (e.g. pets created before multi-pet support, like the old "pet_main" id).
            val idsInStore = petIds(prefs[KEY_PET_IDS])
            val currentId = prefs[KEY_CURRENT_PET]?.takeIf { it.isNotBlank() }
            val onDisk = store.listPetIds()
            // Merge: preserve DataStore order, then append any filesystem-only IDs
            val merged = (idsInStore + onDisk).distinct()
                .let { if (currentId != null && currentId !in it) listOf(currentId) + it else it }
            merged.mapNotNull { store.readPet(it) }
        }

    override suspend fun createPetFromImage(name: String, idleFrame: Bitmap): Pet {
        val id = System.currentTimeMillis().toString()
        store.writeFrame(id, PetAction.IDLE, frameIndex = 1, bitmap = idleFrame)
        val pet = Pet(
            id = id,
            name = name,
            availableActions = setOf(PetAction.IDLE),
            createdAtMillis = System.currentTimeMillis(),
        )
        store.savePet(pet)
        context.dataStore.edit { prefs ->
            val ids = petIds(prefs[KEY_PET_IDS])
            prefs[KEY_PET_IDS] = (ids + id).joinToString(",")
            prefs[KEY_CURRENT_PET] = id
        }
        return pet
    }

    override fun framesOf(petId: String, action: PetAction): List<Bitmap> =
        store.loadFrames(petId, action)

    override fun assetPath(petId: String): String = store.petAssetPath(petId)

    override suspend fun registerCurrentPet(petId: String) {
        context.dataStore.edit { prefs ->
            val ids = petIds(prefs[KEY_PET_IDS])
            if (petId !in ids) prefs[KEY_PET_IDS] = (ids + petId).joinToString(",")
            prefs[KEY_CURRENT_PET] = petId
        }
    }

    override suspend fun switchPet(petId: String) {
        context.dataStore.edit { it[KEY_CURRENT_PET] = petId }
    }

    override suspend fun deletePet(petId: String) {
        store.deletePet(petId)
        context.dataStore.edit { prefs ->
            val remaining = petIds(prefs[KEY_PET_IDS]).filter { it != petId }
            prefs[KEY_PET_IDS] = remaining.joinToString(",")
            if (prefs[KEY_CURRENT_PET] == petId) {
                prefs[KEY_CURRENT_PET] = remaining.lastOrNull() ?: ""
            }
        }
    }

    private fun petIds(raw: String?): List<String> =
        raw?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
}
