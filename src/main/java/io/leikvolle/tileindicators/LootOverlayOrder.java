package io.leikvolle.tileindicators;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Temporarily places the two item overlays after character masking. */
final class LootOverlayOrder
{
    static final float MASK_PRIORITY = Overlay.PRIORITY_HIGHEST + 1f;
    private final Map<Overlay, PriorityChange> changes = new IdentityHashMap<>();

    // Called under the overlay-manager lock, only for lifecycle/config events.
    boolean update(OverlayManager manager, boolean enabled)
    {
        if (!enabled) return restore();
        Set<Overlay> current = Collections.newSetFromMap(new IdentityHashMap<>());
        manager.anyMatch(overlay -> {
            if (isItemOverlay(overlay)) current.add(overlay);
            return false;
        });
        boolean changed = false;
        Iterator<Map.Entry<Overlay, PriorityChange>> iterator = changes.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<Overlay, PriorityChange> entry = iterator.next();
            if (!current.contains(entry.getKey()))
            {
                changed |= restore(entry.getKey(), entry.getValue());
                iterator.remove();
            }
        }
        for (Overlay overlay : current)
        {
            float priority = overlay.getPriority();
            PriorityChange existing = changes.get(overlay);
            if (existing != null && Float.compare(priority, existing.applied) == 0) continue;
            // Respect priorities changed by the overlay's owner in the meantime.
            changes.remove(overlay);
            if (!Float.isFinite(priority) || priority > MASK_PRIORITY) continue;
            float applied = MASK_PRIORITY + 1f + Math.max(0f, priority);
            changes.put(overlay, new PriorityChange(priority, applied));
            overlay.setPriority(applied);
            changed = true;
        }
        return changed;
    }

    boolean restore()
    {
        boolean changed = false;
        for (Map.Entry<Overlay, PriorityChange> entry : changes.entrySet())
            changed |= restore(entry.getKey(), entry.getValue());
        changes.clear();
        return changed;
    }

    private static boolean restore(Overlay overlay, PriorityChange change)
    {
        if (Float.compare(overlay.getPriority(), change.applied) != 0) return false;
        overlay.setPriority(change.original);
        return true;
    }

    private static boolean isItemOverlay(Overlay overlay)
    {
        String name = overlay.getClass().getName();
        // Match the exact public overlay classes; Loot Filters stays optional.
        return (name.equals("net.runelite.client.plugins.grounditems.GroundItemsOverlay")
                || name.equals("com.lootfilters.LootFiltersOverlay"))
                && overlay.getLayer() == OverlayLayer.ABOVE_SCENE
                && overlay.getPosition() == OverlayPosition.DYNAMIC
                && (overlay.getPreferredPosition() == null
                    || overlay.getPreferredPosition() == OverlayPosition.DYNAMIC);
    }

    private static final class PriorityChange
    {
        private final float original;
        private final float applied;

        private PriorityChange(float original, float applied)
        {
            this.original = original;
            this.applied = applied;
        }
    }
}
