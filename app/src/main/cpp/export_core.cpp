#include "export_core.h"

ExportCore::ExportCore() {
    resetStats();
}

ExportCore::~ExportCore() {
}

ExportStats ExportCore::getStats() const {
    return stats;
}

void ExportCore::resetStats() {
    stats.framesProcessed = 0;
    stats.totalRenderTimeMs = 0.0;
    stats.audioSamplesMixed = 0;
}
