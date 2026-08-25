import * as L from 'leaflet';

// leaflet.heat/leaflet.markercluster — старые UMD-плагины, написанные под подключение через
// <script> (ожидают глобальную переменную L), а не под ES-модули. esbuild/Angular не создаёт
// такой глобал сам — выставляем вручную. Этот файл должен импортироваться ДО самих плагинов
// (device-map.ts), иначе плагины падают с "L.markerClusterGroup is not a function"/аналогично
// для heatLayer — их код на верхнем уровне модуля читает L из глобальной области.
(window as unknown as { L: typeof L }).L = L;
