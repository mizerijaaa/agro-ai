import L from "leaflet";

// Vite + Leaflet: fix default marker icon URLs.
import icon2xUrl from "leaflet/dist/images/marker-icon-2x.png";
import iconUrl from "leaflet/dist/images/marker-icon.png";
import shadowUrl from "leaflet/dist/images/marker-shadow.png";

// eslint-disable-next-line no-underscore-dangle
delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
  iconRetinaUrl: icon2xUrl,
  iconUrl,
  shadowUrl,
});

