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

@Singleton
class PetRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: PetStore,
) : PetRepository {

    override val currentPet: Flow<Pet?> =
        context.dataStore.data.map { prefs ->
            prefs[KEY_CURRENT_PET]?.let { store.readPet(it) }
        }

    override suspend fun createPetFromImage(name: String, idleFrame: Bitmap): Pet {
        // id 不用时间戳（保持可重放/可测）——用内容无关的固定单宠 id，MVP 仅一只宠物
        val id = "pet_main"
        store.deletePet(id) // 替换：先清旧帧
        store.writeFrame(id, PetAction.IDLE, frameIndex = 1, bitmap = idleFrame)
        val pet = Pet(
            id = id,
            name = name,
            availableActions = setOf(PetAction.IDLE),
            createdAtMillis = 0L,
        )
        store.savePet(pet)
        context.dataStore.edit { it[KEY_CURRENT_PET] = id }
        return pet
    }

    override fun framesOf(petId: String, action: PetAction): List<Bitmap> =
        store.loadFrames(petId, action)

    override fun assetPath(petId: String): String = store.petAssetPath(petId)
}
