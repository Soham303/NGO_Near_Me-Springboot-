package com.foodrescue.entity.converter;

import com.foodrescue.entity.enums.*;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

public class EnumConverters {

    @Converter(autoApply = true)
    public static class OrgStatusConverter implements AttributeConverter<OrgStatus, String> {
        @Override
        public String convertToDatabaseColumn(OrgStatus attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public OrgStatus convertToEntityAttribute(String dbData) {
            return OrgStatus.fromValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class VenueTypeConverter implements AttributeConverter<VenueType, String> {
        @Override
        public String convertToDatabaseColumn(VenueType attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public VenueType convertToEntityAttribute(String dbData) {
            return VenueType.fromValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class UserRoleConverter implements AttributeConverter<UserRole, String> {
        @Override
        public String convertToDatabaseColumn(UserRole attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public UserRole convertToEntityAttribute(String dbData) {
            return UserRole.fromValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class ListingStatusConverter implements AttributeConverter<ListingStatus, String> {
        @Override
        public String convertToDatabaseColumn(ListingStatus attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public ListingStatus convertToEntityAttribute(String dbData) {
            return ListingStatus.fromValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class FoodDietConverter implements AttributeConverter<FoodDiet, String> {
        @Override
        public String convertToDatabaseColumn(FoodDiet attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public FoodDiet convertToEntityAttribute(String dbData) {
            return FoodDiet.fromValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class PickupStatusConverter implements AttributeConverter<PickupStatus, String> {
        @Override
        public String convertToDatabaseColumn(PickupStatus attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public PickupStatus convertToEntityAttribute(String dbData) {
            return PickupStatus.fromValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class FlagStatusConverter implements AttributeConverter<FlagStatus, String> {
        @Override
        public String convertToDatabaseColumn(FlagStatus attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public FlagStatus convertToEntityAttribute(String dbData) {
            return FlagStatus.fromValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class NewsSourceConverter implements AttributeConverter<NewsSource, String> {
        @Override
        public String convertToDatabaseColumn(NewsSource attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public NewsSource convertToEntityAttribute(String dbData) {
            return NewsSource.fromValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class NewsStatusConverter implements AttributeConverter<NewsStatus, String> {
        @Override
        public String convertToDatabaseColumn(NewsStatus attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public NewsStatus convertToEntityAttribute(String dbData) {
            return NewsStatus.fromValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class TagKindConverter implements AttributeConverter<TagKind, String> {
        @Override
        public String convertToDatabaseColumn(TagKind attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public TagKind convertToEntityAttribute(String dbData) {
            return TagKind.fromValue(dbData);
        }
    }

    @Converter(autoApply = true)
    public static class ScoreTierConverter implements AttributeConverter<ScoreTier, String> {
        @Override
        public String convertToDatabaseColumn(ScoreTier attribute) {
            return attribute == null ? null : attribute.getValue();
        }

        @Override
        public ScoreTier convertToEntityAttribute(String dbData) {
            return ScoreTier.fromValue(dbData);
        }
    }
}
