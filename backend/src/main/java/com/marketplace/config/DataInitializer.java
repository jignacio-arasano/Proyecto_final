package com.marketplace.config;

import com.marketplace.model.IibbRate;
import com.marketplace.model.InstallmentRate;
import com.marketplace.model.PlatformCommission;
import com.marketplace.repository.IibbRateRepository;
import com.marketplace.repository.InstallmentRateRepository;
import com.marketplace.repository.PlatformCommissionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class DataInitializer implements CommandLineRunner {
    private final PlatformCommissionRepository commissionRepo;
    private final IibbRateRepository iibbRepo;
    private final InstallmentRateRepository installmentRepo;

    public DataInitializer(PlatformCommissionRepository commissionRepo,
                           IibbRateRepository iibbRepo,
                           InstallmentRateRepository installmentRepo) {
        this.commissionRepo = commissionRepo;
        this.iibbRepo = iibbRepo;
        this.installmentRepo = installmentRepo;
    }

    @Override
    public void run(String... args) {
        seedCommissions();
        seedInstallmentRates();
        seedIibbRates();
    }

    private void seedCommissions() {
        // Si existe data del formato viejo (Clásico/Premium), la eliminamos y resembramos
        if (commissionRepo.findByCategoryAndModality("Electrónica", "Clásico").isPresent()) {
            commissionRepo.deleteAll();
        }
        if (commissionRepo.count() > 0) return;

        // Comisiones BASE por categoría de Mercado Libre Argentina 2026
        // Fuente: ML Argentina + verificación con múltiples fuentes
        // modality = "Base" indica que es la tasa base sin recargo por cuotas
        List<Object[]> data = List.of(
            new Object[]{"Celulares y Teléfonos",     "Base", "0.0900"},
            new Object[]{"Computación",               "Base", "0.0900"},
            new Object[]{"Electrónica y Audio",       "Base", "0.1100"},
            new Object[]{"Electrodomésticos",         "Base", "0.1100"},
            new Object[]{"Herramientas",              "Base", "0.1100"},
            new Object[]{"Indumentaria y Calzado",    "Base", "0.1300"},
            new Object[]{"Deportes y Fitness",        "Base", "0.1300"},
            new Object[]{"Hogar, Muebles y Jardín",   "Base", "0.1300"},
            new Object[]{"Juegos y Juguetes",         "Base", "0.1300"},
            new Object[]{"Alimentos y Bebidas",       "Base", "0.1300"},
            new Object[]{"Salud y Belleza",           "Base", "0.1300"},
            new Object[]{"Otras categorías",          "Base", "0.1300"}
        );

        for (Object[] row : data) {
            PlatformCommission c = new PlatformCommission();
            c.setCategory((String) row[0]);
            c.setModality((String) row[1]);
            c.setRate(new BigDecimal((String) row[2]));
            commissionRepo.save(c);
        }
    }

    private void seedInstallmentRates() {
        if (installmentRepo.count() > 0) return;

        // Recargos por opción de cuotas de Mercado Libre Argentina 2026
        // Se suman a la comisión base por categoría
        List<Object[]> data = List.of(
            new Object[]{"Sin cuotas",                      "0.0000"},
            new Object[]{"Cuotas con interés bajo",         "0.0500"},
            new Object[]{"Cuotas al mismo precio 3 cuotas", "0.0840"},
            new Object[]{"Cuotas al mismo precio 6 cuotas", "0.1230"},
            new Object[]{"Cuotas al mismo precio 9 cuotas", "0.1570"},
            new Object[]{"Cuotas al mismo precio 12 cuotas","0.1920"}
        );

        for (Object[] row : data) {
            InstallmentRate r = new InstallmentRate();
            r.setOptionName((String) row[0]);
            r.setSurchargeRate(new BigDecimal((String) row[1]));
            installmentRepo.save(r);
        }
    }

    private void seedIibbRates() {
        if (iibbRepo.count() > 0) return;

        // Alícuotas de IIBB para comercio electrónico/minorista por provincia - 2026
        // Fuentes: leyes impositivas provinciales 2026, Contablix, fuentes oficiales
        Map<String, String> rates = Map.ofEntries(
            Map.entry("Buenos Aires",        "0.0350"),
            Map.entry("CABA",                "0.0300"),
            Map.entry("Córdoba",             "0.0350"),
            Map.entry("Santa Fe",            "0.0350"),
            Map.entry("Mendoza",             "0.0350"),
            Map.entry("Tucumán",             "0.0300"),
            Map.entry("Entre Ríos",          "0.0350"),
            Map.entry("Salta",               "0.0500"),
            Map.entry("Jujuy",               "0.0450"),
            Map.entry("Misiones",            "0.0450"),
            Map.entry("Chaco",               "0.0290"),
            Map.entry("Corrientes",          "0.0290"),
            Map.entry("Santiago del Estero", "0.0350"),
            Map.entry("San Juan",            "0.0350"),
            Map.entry("Río Negro",           "0.0350"),
            Map.entry("Neuquén",             "0.0500"),
            Map.entry("Formosa",             "0.0350"),
            Map.entry("Chubut",              "0.0500"),
            Map.entry("San Luis",            "0.0350"),
            Map.entry("Catamarca",           "0.0350"),
            Map.entry("La Rioja",            "0.0350"),
            Map.entry("La Pampa",            "0.0350"),
            Map.entry("Santa Cruz",          "0.0300"),
            Map.entry("Tierra del Fuego",    "0.0300")
        );

        for (Map.Entry<String, String> entry : rates.entrySet()) {
            IibbRate r = new IibbRate();
            r.setProvince(entry.getKey());
            r.setRate(new BigDecimal(entry.getValue()));
            iibbRepo.save(r);
        }
    }
}
