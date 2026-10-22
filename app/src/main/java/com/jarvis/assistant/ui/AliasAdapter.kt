package com.jarvis.assistant.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.R
import com.jarvis.assistant.data.entity.AliasEntity

class AliasAdapter(
    private val onDeleteClick: (AliasEntity) -> Unit
) : ListAdapter<AliasEntity, AliasAdapter.AliasViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AliasViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_alias, parent, false)
        return AliasViewHolder(view, onDeleteClick)
    }

    override fun onBindViewHolder(holder: AliasViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AliasViewHolder(
        itemView: View,
        private val onDeleteClick: (AliasEntity) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvTag: TextView = itemView.findViewById(R.id.tvTag)
        private val tvContactName: TextView = itemView.findViewById(R.id.tvContactName)
        private val tvPhoneNumber: TextView = itemView.findViewById(R.id.tvPhoneNumber)
        private val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)

        fun bind(alias: AliasEntity) {
            tvTag.text = alias.relationshipTag
            tvContactName.text = alias.actualContactName
            if (!alias.phoneNumber.isNullOrBlank()) {
                tvPhoneNumber.text = alias.phoneNumber
                tvPhoneNumber.visibility = View.VISIBLE
            } else {
                tvPhoneNumber.visibility = View.GONE
            }

            btnDelete.setOnClickListener {
                onDeleteClick(alias)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<AliasEntity>() {
        override fun areItemsTheSame(oldItem: AliasEntity, newItem: AliasEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AliasEntity, newItem: AliasEntity): Boolean {
            return oldItem == newItem
        }
    }
}
