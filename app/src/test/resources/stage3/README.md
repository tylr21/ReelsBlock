These fake node trees retain only resource IDs, bounds, selection and hierarchy
from the local captures listed in docs/rules-map.md. All text, descriptions and
fixed-label tags were removed. They are test inputs, not raw device dumps.

dm_reel_before_swipe.xml and dm_reel_after_swipe.xml were transcribed from the
redacted service dumps captured on 2026-11-06, with the user performing the swipe.
The observed pager emitted two identical TYPE_VIEW_SCROLLED events with
fromIndex=1, toIndex=1, scrollY=0 and itemCount=7. The media item changed hierarchy
path inside the pager and the reply composer disappeared. Bounds changed too,
but layout dimensions alone are not a reel fingerprint.
